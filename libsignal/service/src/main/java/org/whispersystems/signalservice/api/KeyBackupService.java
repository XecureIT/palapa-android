package org.whispersystems.signalservice.api;

import org.whispersystems.libsignal.logging.Log;
import org.whispersystems.signalservice.api.crypto.InvalidCiphertextException;
import org.whispersystems.signalservice.internal.contacts.crypto.KeyBackupCipher;
import org.whispersystems.signalservice.internal.contacts.crypto.Quote;
import org.whispersystems.signalservice.internal.contacts.crypto.RemoteAttestation;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedQuoteException;
import org.whispersystems.signalservice.internal.contacts.crypto.UnauthenticatedResponseException;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupRequest;
import org.whispersystems.signalservice.internal.contacts.entities.KeyBackupResponse;
import org.whispersystems.signalservice.internal.contacts.entities.TokenResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.BackupResponse;
import org.whispersystems.signalservice.internal.keybackup.protos.RestoreResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.RemoteAttestationUtil;
import org.whispersystems.signalservice.internal.registrationpin.InvalidPinException;
import org.whispersystems.signalservice.internal.registrationpin.PinStretcher;
import org.whispersystems.signalservice.internal.util.Hex;
import org.whispersystems.signalservice.internal.util.Util;

import java.io.IOException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.SignatureException;
import java.util.Locale;

public final class KeyBackupService {

  private static final String TAG = KeyBackupService.class.getSimpleName();

  private final KeyStore          iasKeyStore;
  private final String            enclaveName;
  private final String            mrenclave;
  private final PushServiceSocket pushServiceSocket;
  private final int               maxTries;

  KeyBackupService(KeyStore iasKeyStore,
                   String enclaveName,
                   String mrenclave,
                   PushServiceSocket pushServiceSocket,
                   int maxTries)
  {
    this.iasKeyStore       = iasKeyStore;
    this.enclaveName       = enclaveName;
    this.mrenclave         = mrenclave;
    this.pushServiceSocket = pushServiceSocket;
    this.maxTries          = maxTries;
  }

  /**
   * Use this if you don't want to validate that the server has not changed since you last set the pin.
   */
  public PinChangeSession newPinChangeSession()
    throws IOException
  {
    return newSession(pushServiceSocket.getKeyBackupServiceAuthorization(), null);
  }

  /**
   * Use this if you want to validate that the server has not changed since you last set the pin.
   * The supplied token will have to match for the change to be successful.
   */
  public PinChangeSession newPinChangeSession(TokenResponse currentToken)
    throws IOException
  {
    return newSession(pushServiceSocket.getKeyBackupServiceAuthorization(), currentToken);
  }

  /**
   * Use this to validate that the pin is still set on the server with the current token.
   * Additionally this validates that no one has used any tries.
   */
  public RestoreSession newRestoreSession(TokenResponse currentToken)
    throws IOException
  {
    return newSession(pushServiceSocket.getKeyBackupServiceAuthorization(), currentToken);
  }

  /**
   * Only call before registration, to see how many tries are left.
   * <p>
   * Pass the token to the newRegistrationSession.
   */
  public TokenResponse getToken(String authAuthorization) throws IOException {
    return pushServiceSocket.getKeyBackupServiceToken(authAuthorization, enclaveName);
  }

  /**
   * Use this during registration, good for one try, on subsequent attempts, pass the token from the previous attempt.
   *
   * @param tokenResponse Supplying a token response from a failed previous attempt prevents certain attacks.
   */
  public RestoreSession newRegistrationSession(String authAuthorization, TokenResponse tokenResponse)
    throws IOException
  {
    return newSession(authAuthorization, tokenResponse);
  }

  private Session newSession(String authorization, TokenResponse currentToken)
    throws IOException
  {
    TokenResponse token = currentToken != null ? currentToken : pushServiceSocket.getKeyBackupServiceToken(authorization, enclaveName);

    return new Session(authorization, token);
  }

  private class Session implements RestoreSession, PinChangeSession {

    private final String        authorization;
    private final TokenResponse currentToken;

    Session(String authorization, TokenResponse currentToken) {
      this.authorization = authorization;
      this.currentToken  = currentToken;
    }

    @Override
    public RegistrationLockData restorePin(String pin)
      throws UnauthenticatedResponseException, IOException, KeyBackupServicePinException, InvalidPinException
    {
      int           attempt = 0;
      SecureRandom  random  = new SecureRandom();
      TokenResponse token   = currentToken;

      while (true) {

        attempt++;

        try {
          return restorePin(pin, token);
        } catch (TokenException tokenException) {

          token = tokenException.getToken();

          if (tokenException instanceof KeyBackupServicePinException) {
            throw (KeyBackupServicePinException) tokenException;
          }

          if (tokenException.isCanAutomaticallyRetry() && attempt < 5) {
            // back off randomly, between 250 and 8000 ms
            int backoffMs = 250 * (1 << (attempt - 1));

            Util.sleep(backoffMs + random.nextInt(backoffMs));
          } else {
            throw new UnauthenticatedResponseException("Token mismatch, expended all automatic retries");
          }
        }
      }
    }

    private RegistrationLockData restorePin(String pin, TokenResponse token)
      throws UnauthenticatedResponseException, IOException, TokenException, InvalidPinException
    {
      PinStretcher.StretchedPin stretchedPin = PinStretcher.stretchPin(pin);

      try {
        final int               remainingTries    = token.getTries();
        final RemoteAttestation remoteAttestation = getAndVerifyRemoteAttestation();
        final KeyBackupRequest  request           = KeyBackupCipher.createKeyRestoreRequest(stretchedPin.getKbsAccessKey(), token, remoteAttestation, Hex.fromStringCondensed(enclaveName));
        final KeyBackupResponse response          = pushServiceSocket.putKbsData(authorization, request, remoteAttestation.getCookies(), enclaveName);
        final RestoreResponse   status            = KeyBackupCipher.getKeyRestoreResponse(response, remoteAttestation);

        TokenResponse nextToken = status.hasToken()
                                  ? new TokenResponse(token.getBackupId(), status.getToken().toByteArray(), status.getTries())
                                  : token;

        Log.i(TAG, "Restore " + status.getStatus());
        switch (status.getStatus()) {
          case OK:
            Log.i(TAG, String.format(Locale.US,"Restore OK! data: %s tries: %d", Hex.toStringCondensed(status.getData().toByteArray()), status.getTries()));
            PinStretcher.MasterKey masterKey = stretchedPin.withPinKey2(status.getData().toByteArray());
            return new RegistrationLockData(masterKey, nextToken);
          case PIN_MISMATCH:
            Log.i(TAG, "Restore PIN_MISMATCH");
            throw new KeyBackupServicePinException(nextToken);
          case TOKEN_MISMATCH:
            Log.i(TAG, "Restore TOKEN_MISMATCH");
            // if the number of tries has not fallen, the pin is correct we're just using an out of date token
            boolean canRetry = remainingTries == status.getTries();
            Log.i(TAG, String.format(Locale.US, "Token MISMATCH %d %d", remainingTries, status.getTries()));
            Log.i(TAG, String.format("Last token %s", Hex.toStringCondensed(token.getToken())));
            Log.i(TAG, String.format("Next token %s", Hex.toStringCondensed(nextToken.getToken())));
            throw new TokenException(nextToken, canRetry);
          case MISSING:
            Log.i(TAG, "Restore OK! No data though");
            return null;
          case NOT_YET_VALID:
            throw new UnauthenticatedResponseException("Key is not valid yet, clock mismatch");
        }
      } catch (InvalidCiphertextException e) {
        throw new UnauthenticatedResponseException(e);
      }
      return null;
    }

    private RemoteAttestation getAndVerifyRemoteAttestation() throws UnauthenticatedResponseException, IOException {
      try {
        return RemoteAttestationUtil.getAndVerifyRemoteAttestation(pushServiceSocket, PushServiceSocket.ClientSet.KeyBackup, iasKeyStore, enclaveName, mrenclave, authorization);
      } catch (Quote.InvalidQuoteFormatException | UnauthenticatedQuoteException | InvalidCiphertextException | SignatureException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }

    @Override
    public RegistrationLockData setPin(String pin) throws IOException, UnauthenticatedResponseException, InvalidPinException {
      PinStretcher.MasterKey masterKey = PinStretcher.stretchPin(pin)
                                                     .withNewSecurePinKey2();

      TokenResponse tokenResponse = putKbsData(masterKey.getKbsAccessKey(),
                                               masterKey.getPinKey2(),
                                               enclaveName,
                                               currentToken);

      pushServiceSocket.setRegistrationLock(masterKey.getRegistrationLock());

      return new RegistrationLockData(masterKey, tokenResponse);
    }

    @Override
    public void removePin() throws IOException, UnauthenticatedResponseException {
      deleteKbsData();

      pushServiceSocket.removePinV2();
    }

    private TokenResponse putKbsData(byte[] kbsAccessKey, byte[] kbsData, String enclaveName, TokenResponse token)
        throws IOException, UnauthenticatedResponseException
    {
      try {
        RemoteAttestation     remoteAttestation = getAndVerifyRemoteAttestation();
        KeyBackupRequest      request           = KeyBackupCipher.createKeyBackupRequest(kbsAccessKey, kbsData, token, remoteAttestation, Hex.fromStringCondensed(enclaveName), maxTries);
        KeyBackupResponse     response          = pushServiceSocket.putKbsData(authorization, request, remoteAttestation.getCookies(), enclaveName);
        BackupResponse        backupResponse    = KeyBackupCipher.getKeyBackupResponse(response, remoteAttestation);
        BackupResponse.Status status            = backupResponse.getStatus();

        switch (status) {
          case OK:
            return backupResponse.hasToken() ? new TokenResponse(token.getBackupId(), backupResponse.getToken().toByteArray(), maxTries) : token;
          case ALREADY_EXISTS:
            throw new UnauthenticatedResponseException("Already exists");
          case NOT_YET_VALID:
            throw new UnauthenticatedResponseException("Key is not valid yet, clock mismatch");
          default:
            throw new AssertionError("Unknown response status " + status);
        }
      } catch (InvalidCiphertextException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }

    private void deleteKbsData()
      throws IOException, UnauthenticatedResponseException
    {
      try {
        RemoteAttestation remoteAttestation = getAndVerifyRemoteAttestation();
        KeyBackupRequest  request           = KeyBackupCipher.createKeyDeleteRequest(currentToken, remoteAttestation, Hex.fromStringCondensed(enclaveName));
        KeyBackupResponse response          = pushServiceSocket.putKbsData(authorization, request, remoteAttestation.getCookies(), enclaveName);

        KeyBackupCipher.getKeyDeleteResponseStatus(response, remoteAttestation);
      } catch (InvalidCiphertextException e) {
        throw new UnauthenticatedResponseException(e);
      }
    }
  }

  public interface RestoreSession {

    RegistrationLockData restorePin(String pin)
      throws UnauthenticatedResponseException, IOException, KeyBackupServicePinException, InvalidPinException;
  }

  public interface PinChangeSession {

    RegistrationLockData setPin(String pin)
      throws IOException, UnauthenticatedResponseException, InvalidPinException;

    void removePin()
      throws IOException, UnauthenticatedResponseException;
  }
}
