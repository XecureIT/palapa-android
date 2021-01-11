package org.thoughtcrime.securesms.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import com.annimon.stream.Stream;
import com.google.protobuf.InvalidProtocolBufferException;

import net.sqlcipher.database.SQLiteDatabase;

import org.thoughtcrime.securesms.database.documents.Document;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatch;
import org.thoughtcrime.securesms.database.documents.IdentityKeyMismatchList;
import org.thoughtcrime.securesms.database.helpers.SQLCipherOpenHelper;
import org.thoughtcrime.securesms.database.model.DatabaseProtos.ReactionList;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.insights.InsightsConstants;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.JsonUtils;
import org.whispersystems.libsignal.IdentityKey;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public abstract class MessagingDatabase extends Database implements MmsSmsColumns {

  private static final String TAG = MessagingDatabase.class.getSimpleName();

  public MessagingDatabase(Context context, SQLCipherOpenHelper databaseHelper) {
    super(context, databaseHelper);
  }

  protected abstract String getTableName();
  protected abstract String getTypeField();
  protected abstract String getDateSentColumnName();

  public abstract void markExpireStarted(long messageId);
  public abstract void markExpireStarted(long messageId, long startTime);

  public abstract void markAsSent(long messageId, boolean secure);
  public abstract void markUnidentified(long messageId, boolean unidentified);

  final int getInsecureMessagesSentForThread(long threadId) {
    SQLiteDatabase db         = databaseHelper.getReadableDatabase();
    String[]       projection = new String[]{"COUNT(*)"};
    String         query      = THREAD_ID + " = ? AND " + getOutgoingInsecureMessageClause() + " AND " + getDateSentColumnName() + " > ?";
    String[]       args       = new String[]{String.valueOf(threadId), String.valueOf(System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  final int getInsecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingInsecureMessageClause());
  }

  final int getSecureMessageCountForInsights() {
    return getMessageCountForRecipientsAndType(getOutgoingSecureMessageClause());
  }

  private int getMessageCountForRecipientsAndType(String typeClause) {

    SQLiteDatabase db           = databaseHelper.getReadableDatabase();
    String[]       projection   = new String[] {"COUNT(*)"};
    String         query        = typeClause + " AND " + getDateSentColumnName() + " > ?";
    String[]       args         = new String[]{String.valueOf(System.currentTimeMillis() - InsightsConstants.PERIOD_IN_MILLIS)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getInt(0);
      } else {
        return 0;
      }
    }
  }

  private String getOutgoingInsecureMessageClause() {
    return "(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND NOT (" + getTypeField() + " & " + Types.SECURE_MESSAGE_BIT + ")";
  }

  private String getOutgoingSecureMessageClause() {
    return "(" + getTypeField() + " & " + Types.BASE_TYPE_MASK + ") = " + Types.BASE_SENT_TYPE + " AND (" + getTypeField() + " & " + (Types.SECURE_MESSAGE_BIT | Types.PUSH_MESSAGE_BIT) + ")";
  }

  public void setReactionsSeen(long threadId) {
    SQLiteDatabase db          = databaseHelper.getWritableDatabase();
    ContentValues  values      = new ContentValues();
    String         whereClause = THREAD_ID + " = ? AND " + REACTIONS_UNREAD + " = ?";
    String[]       whereArgs   = new String[]{String.valueOf(threadId), "1"};

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(getTableName(), values, whereClause, whereArgs);
  }

  public void setAllReactionsSeen() {
    SQLiteDatabase db          = databaseHelper.getWritableDatabase();
    ContentValues  values      = new ContentValues();

    values.put(REACTIONS_UNREAD, 0);
    values.put(REACTIONS_LAST_SEEN, System.currentTimeMillis());

    db.update(getTableName(), values, null, null);
  }

  public void addReaction(long messageId, @NonNull ReactionRecord reaction) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      ReactionList          reactions   = getReactions(db, messageId).or(ReactionList.getDefaultInstance());
      ReactionList.Reaction newReaction = ReactionList.Reaction.newBuilder()
                                                               .setEmoji(reaction.getEmoji())
                                                               .setAuthor(reaction.getAuthor().toLong())
                                                               .setSentTime(reaction.getDateSent())
                                                               .setReceivedTime(reaction.getDateReceived())
                                                               .build();

      ReactionList updatedList = pruneByAuthor(reactions, reaction.getAuthor()).toBuilder()
                                                                               .addReactions(newReaction)
                                                                               .build();

      setReactions(db, messageId, updatedList);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(getThreadId(db, messageId));
  }

  public void deleteReaction(long messageId, @NonNull RecipientId author) {
    SQLiteDatabase db = databaseHelper.getWritableDatabase();

    db.beginTransaction();

    try {
      ReactionList reactions   = getReactions(db, messageId).or(ReactionList.getDefaultInstance());
      ReactionList updatedList = pruneByAuthor(reactions, author);

      setReactions(db, messageId, updatedList);

      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }

    notifyConversationListeners(getThreadId(db, messageId));
  }

  public boolean hasReaction(long messageId, @NonNull ReactionRecord reactionRecord) {
    SQLiteDatabase db = databaseHelper.getReadableDatabase();

    ReactionList reactions = getReactions(db, messageId).or(ReactionList.getDefaultInstance());

    for (ReactionList.Reaction reaction : reactions.getReactionsList()) {
      if (reactionRecord.getAuthor().toLong() == reaction.getAuthor() &&
          reactionRecord.getEmoji().equals(reaction.getEmoji()))
      {
        return true;
      }
    }

    return false;
  }

  public void addMismatchedIdentity(long messageId, @NonNull RecipientId recipientId, IdentityKey identityKey) {
    try {
      addToDocument(messageId, MISMATCHED_IDENTITIES,
                    new IdentityKeyMismatch(recipientId, identityKey),
                    IdentityKeyMismatchList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  public void removeMismatchedIdentity(long messageId, @NonNull RecipientId recipientId, IdentityKey identityKey) {
    try {
      removeFromDocument(messageId, MISMATCHED_IDENTITIES,
                         new IdentityKeyMismatch(recipientId, identityKey),
                         IdentityKeyMismatchList.class);
    } catch (IOException e) {
      Log.w(TAG, e);
    }
  }

  protected List<ReactionRecord> parseReactions(@NonNull Cursor cursor) {
    byte[] raw = cursor.getBlob(cursor.getColumnIndexOrThrow(REACTIONS));

    if (raw != null) {
      try {
        return Stream.of(ReactionList.parseFrom(raw).getReactionsList())
                     .map(r -> {
                       return new ReactionRecord(r.getEmoji(),
                                                 RecipientId.from(r.getAuthor()),
                                                 r.getSentTime(),
                                                 r.getReceivedTime());
                     })
                     .toList();
      } catch (InvalidProtocolBufferException e) {
        Log.w(TAG, "[parseReactions] Failed to parse reaction list!", e);
        return Collections.emptyList();
      }
    } else {
      return Collections.emptyList();
    }
  }

  protected <D extends Document<I>, I> void removeFromDocument(long messageId, String column, I object, Class<D> clazz) throws IOException {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      D           document = getDocument(database, messageId, column, clazz);
      Iterator<I> iterator = document.getList().iterator();

      while (iterator.hasNext()) {
        I item = iterator.next();

        if (item.equals(object)) {
          iterator.remove();
          break;
        }
      }

      setDocument(database, messageId, column, document);
      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  protected <T extends Document<I>, I> void addToDocument(long messageId, String column, final I object, Class<T> clazz) throws IOException {
    List<I> list = new ArrayList<I>() {{
      add(object);
    }};

    addToDocument(messageId, column, list, clazz);
  }

  protected <T extends Document<I>, I> void addToDocument(long messageId, String column, List<I> objects, Class<T> clazz) throws IOException {
    SQLiteDatabase database = databaseHelper.getWritableDatabase();
    database.beginTransaction();

    try {
      T document = getDocument(database, messageId, column, clazz);
      document.getList().addAll(objects);
      setDocument(database, messageId, column, document);

      database.setTransactionSuccessful();
    } finally {
      database.endTransaction();
    }
  }

  private void setDocument(SQLiteDatabase database, long messageId, String column, Document document) throws IOException {
    ContentValues contentValues = new ContentValues();

    if (document == null || document.size() == 0) {
      contentValues.put(column, (String)null);
    } else {
      contentValues.put(column, JsonUtils.toJson(document));
    }

    database.update(getTableName(), contentValues, ID_WHERE, new String[] {String.valueOf(messageId)});
  }

  private <D extends Document> D getDocument(SQLiteDatabase database, long messageId,
                                             String column, Class<D> clazz)
  {
    Cursor cursor = null;

    try {
      cursor = database.query(getTableName(), new String[] {column},
                              ID_WHERE, new String[] {String.valueOf(messageId)},
                              null, null, null);

      if (cursor != null && cursor.moveToNext()) {
        String document = cursor.getString(cursor.getColumnIndexOrThrow(column));

        try {
          if (!TextUtils.isEmpty(document)) {
            return JsonUtils.fromJson(document, clazz);
          }
        } catch (IOException e) {
          Log.w(TAG, e);
        }
      }

      try {
        return clazz.newInstance();
      } catch (InstantiationException e) {
        throw new AssertionError(e);
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }

    } finally {
      if (cursor != null)
        cursor.close();
    }
  }

  private static @NonNull ReactionList pruneByAuthor(@NonNull ReactionList reactionList, @NonNull RecipientId recipientId) {
    List<ReactionList.Reaction> pruned = Stream.of(reactionList.getReactionsList())
                                               .filterNot(r -> r.getAuthor() == recipientId.toLong())
                                               .toList();

    return reactionList.toBuilder()
                       .clearReactions()
                       .addAllReactions(pruned)
                       .build();
  }

  private @NonNull Optional<ReactionList> getReactions(SQLiteDatabase db, long messageId) {
    String[] projection = new String[]{ REACTIONS };
    String   query      = ID + " = ?";
    String[] args       = new String[]{String.valueOf(messageId)};

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        byte[] raw = cursor.getBlob(cursor.getColumnIndexOrThrow(REACTIONS));

        if (raw != null) {
          return Optional.of(ReactionList.parseFrom(raw));
        }
      }
    } catch (InvalidProtocolBufferException e) {
      Log.w(TAG, "[getRecipients] Failed to parse reaction list!", e);
    }

    return Optional.absent();
  }

  private void setReactions(@NonNull SQLiteDatabase db, long messageId, @NonNull ReactionList reactionList) {
    ContentValues values = new ContentValues(1);
    values.put(REACTIONS, reactionList.getReactionsList().isEmpty() ? null : reactionList.toByteArray());
    values.put(REACTIONS_UNREAD, reactionList.getReactionsCount() != 0 ? 1 : 0);

    String   query = ID + " = ?";
    String[] args  = new String[] { String.valueOf(messageId) };

    db.update(getTableName(), values, query, args);
  }

  private long getThreadId(@NonNull SQLiteDatabase db, long messageId) {
    String[] projection = new String[]{ THREAD_ID };
    String   query      = ID + " = ?";
    String[] args       = new String[]{ String.valueOf(messageId) };

    try (Cursor cursor = db.query(getTableName(), projection, query, args, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getLong(cursor.getColumnIndexOrThrow(THREAD_ID));
      }
    }

    return -1;
  }

  public static class SyncMessageId {

    private final RecipientId recipientId;
    private final long        timetamp;

    public SyncMessageId(@NonNull RecipientId recipientId, long timetamp) {
      this.recipientId = recipientId;
      this.timetamp    = timetamp;
    }

    public RecipientId getRecipientId() {
      return recipientId;
    }

    public long getTimetamp() {
      return timetamp;
    }
  }

  public static class ExpirationInfo {

    private final long    id;
    private final long    expiresIn;
    private final long    expireStarted;
    private final boolean mms;

    public ExpirationInfo(long id, long expiresIn, long expireStarted, boolean mms) {
      this.id            = id;
      this.expiresIn     = expiresIn;
      this.expireStarted = expireStarted;
      this.mms           = mms;
    }

    public long getId() {
      return id;
    }

    public long getExpiresIn() {
      return expiresIn;
    }

    public long getExpireStarted() {
      return expireStarted;
    }

    public boolean isMms() {
      return mms;
    }
  }

  public static class MarkedMessageInfo {

    private final SyncMessageId  syncMessageId;
    private final ExpirationInfo expirationInfo;

    public MarkedMessageInfo(SyncMessageId syncMessageId, ExpirationInfo expirationInfo) {
      this.syncMessageId  = syncMessageId;
      this.expirationInfo = expirationInfo;
    }

    public SyncMessageId getSyncMessageId() {
      return syncMessageId;
    }

    public ExpirationInfo getExpirationInfo() {
      return expirationInfo;
    }
  }

  public static class InsertResult {
    private final long messageId;
    private final long threadId;

    public InsertResult(long messageId, long threadId) {
      this.messageId = messageId;
      this.threadId = threadId;
    }

    public long getMessageId() {
      return messageId;
    }

    public long getThreadId() {
      return threadId;
    }
  }
}
