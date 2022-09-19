package org.thoughtcrime.securesms.dependencies;

import android.app.Application;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.IncomingMessageProcessor;
import org.thoughtcrime.securesms.gcm.MessageRetriever;
import org.thoughtcrime.securesms.jobmanager.JobManager;
import org.thoughtcrime.securesms.push.SignalServiceNetworkAccess;
import org.thoughtcrime.securesms.recipients.LiveRecipientCache;
import org.thoughtcrime.securesms.service.IncomingMessageObserver;
import org.thoughtcrime.securesms.service.webrtc.SignalCallManager;
import org.thoughtcrime.securesms.util.AppForegroundObserver;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.FrameRateTracker;
import org.thoughtcrime.securesms.util.IasKeyStore;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.whispersystems.signalservice.api.KeyBackupService;
import org.whispersystems.signalservice.api.SignalServiceAccountManager;
import org.whispersystems.signalservice.api.SignalServiceMessageReceiver;
import org.whispersystems.signalservice.api.SignalServiceMessageSender;

/**
 * Location for storing and retrieving application-scoped singletons. Users must call
 * {@link #init(Application, Provider)} before using any of the methods, preferably early on in
 * {@link Application#onCreate()}.
 *
 * All future application-scoped singletons should be written as normal objects, then placed here
 * to manage their singleton-ness.
 */
public class ApplicationDependencies {

  private static final Object LOCK                    = new Object();
  private static final Object FRAME_RATE_TRACKER_LOCK = new Object();
  private static final Object JOB_MANAGER_LOCK        = new Object();

  private static Application           application;
  private static Provider              provider;
  private static AppForegroundObserver appForegroundObserver;

  private static volatile SignalServiceAccountManager  accountManager;
  private static volatile SignalServiceMessageSender   messageSender;
  private static volatile SignalServiceMessageReceiver messageReceiver;
  private static volatile IncomingMessageProcessor     incomingMessageProcessor;
  private static volatile MessageRetriever             messageRetriever;
  private static volatile LiveRecipientCache           recipientCache;
  private static volatile JobManager                   jobManager;
  private static volatile FrameRateTracker             frameRateTracker;
  private static volatile SignalCallManager            signalCallManager;

  @MainThread
  public static void init(@NonNull Application application, @NonNull Provider provider) {
    synchronized (LOCK) {
      if (ApplicationDependencies.application != null || ApplicationDependencies.provider != null) {
        throw new IllegalStateException("Already initialized!");
      }

      ApplicationDependencies.application           = application;
      ApplicationDependencies.provider              = provider;
      ApplicationDependencies.appForegroundObserver = provider.provideAppForegroundObserver();

      ApplicationDependencies.appForegroundObserver.begin();
    }
  }

  public static @NonNull Application getApplication() {
    return application;
  }

  public static @NonNull SignalServiceAccountManager getSignalServiceAccountManager() {
    SignalServiceAccountManager local = accountManager;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (accountManager == null) {
        accountManager = provider.provideSignalServiceAccountManager();
      }
      return accountManager;
    }
  }

  public static @NonNull KeyBackupService getKeyBackupService() {
    if (!FeatureFlags.KBS) throw new AssertionError();
    return getSignalServiceAccountManager().getKeyBackupService(IasKeyStore.getIasKeyStore(application),
                                                                BuildConfig.KEY_BACKUP_ENCLAVE_NAME,
                                                                BuildConfig.KEY_BACKUP_MRENCLAVE,
                                                                10);
  }

  public static @NonNull SignalServiceMessageSender getSignalServiceMessageSender() {
    SignalServiceMessageSender local = messageSender;

    if (local != null) {
      return local;
    }

    synchronized (LOCK) {
      if (messageSender == null) {
        messageSender = provider.provideSignalServiceMessageSender();
      } else {
        messageSender.setMessagePipe(IncomingMessageObserver.getPipe(), IncomingMessageObserver.getUnidentifiedPipe());
        messageSender.setIsMultiDevice(TextSecurePreferences.isMultiDevice(application));
      }

      return messageSender;
    }
  }

  public static @NonNull SignalServiceMessageReceiver getSignalServiceMessageReceiver() {
    synchronized (LOCK) {
      if (messageReceiver == null) {
        messageReceiver = provider.provideSignalServiceMessageReceiver();
      }
      return messageReceiver;
    }
  }

  public static void resetSignalServiceMessageReceiver() {
    synchronized (LOCK) {
      messageReceiver = null;
    }
  }

  public static @NonNull SignalServiceNetworkAccess getSignalServiceNetworkAccess() {
    return provider.provideSignalServiceNetworkAccess();
  }

  public static @NonNull IncomingMessageProcessor getIncomingMessageProcessor() {
    if (incomingMessageProcessor == null) {
      synchronized (LOCK) {
        if (incomingMessageProcessor == null) {
          incomingMessageProcessor = provider.provideIncomingMessageProcessor();
        }
      }
    }

    return incomingMessageProcessor;
  }

  public static @NonNull MessageRetriever getMessageRetriever() {
    if (messageRetriever == null) {
      synchronized (LOCK) {
        if (messageRetriever == null) {
          messageRetriever = provider.provideMessageRetriever();
        }
      }
    }

    return messageRetriever;
  }

  public static @NonNull LiveRecipientCache getRecipientCache() {
    if (recipientCache == null) {
      synchronized (LOCK) {
        if (recipientCache == null) {
          recipientCache = provider.provideRecipientCache();
        }
      }
    }

    return recipientCache;
  }

  public static @NonNull JobManager getJobManager() {
    if (jobManager == null) {
      synchronized (JOB_MANAGER_LOCK) {
        if (jobManager == null) {
          jobManager = provider.provideJobManager();
        }
      }
    }

    return jobManager;
  }

  public static @NonNull FrameRateTracker getFrameRateTracker() {
    if (frameRateTracker == null) {
      synchronized (FRAME_RATE_TRACKER_LOCK) {
        if (frameRateTracker == null) {
          frameRateTracker = provider.provideFrameRateTracker();
        }
      }
    }

    return frameRateTracker;
  }

  public static @NonNull SignalCallManager getSignalCallManager() {
    if (signalCallManager == null) {
      synchronized (LOCK) {
        if (signalCallManager == null) {
          if (signalCallManager == null) {
            signalCallManager = provider.provideSignalCallManager();
          }
        }
      }
    }

    return signalCallManager;
  }

  public static @NonNull AppForegroundObserver getAppForegroundObserver() {
    return appForegroundObserver;
  }

  public interface Provider {
    @NonNull SignalServiceAccountManager provideSignalServiceAccountManager();
    @NonNull SignalServiceMessageSender provideSignalServiceMessageSender();
    @NonNull SignalServiceMessageReceiver provideSignalServiceMessageReceiver();
    @NonNull SignalServiceNetworkAccess provideSignalServiceNetworkAccess();
    @NonNull IncomingMessageProcessor provideIncomingMessageProcessor();
    @NonNull MessageRetriever provideMessageRetriever();
    @NonNull LiveRecipientCache provideRecipientCache();
    @NonNull JobManager provideJobManager();
    @NonNull FrameRateTracker provideFrameRateTracker();
    @NonNull AppForegroundObserver provideAppForegroundObserver();
    @NonNull SignalCallManager provideSignalCallManager();
  }

  private static class UninitializedException extends IllegalStateException {
    private UninitializedException() {
      super("You must call init() first!");
    }
  }
}
