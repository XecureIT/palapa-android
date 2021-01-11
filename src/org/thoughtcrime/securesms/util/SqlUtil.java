package org.thoughtcrime.securesms.util;

import android.content.ContentValues;
import android.database.Cursor;

import androidx.annotation.NonNull;

import net.sqlcipher.database.SQLiteDatabase;

import org.whispersystems.libsignal.util.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SqlUtil {
  private SqlUtil() {}


  public static boolean tableExists(@NonNull SQLiteDatabase db, @NonNull String table) {
    try (Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type=? AND name=?", new String[] { "table", table })) {
      return cursor != null && cursor.moveToNext();
    }
  }

  public static boolean columnExists(@NonNull SQLiteDatabase db, @NonNull String table, @NonNull String column) {
    try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
      int nameColumnIndex = cursor.getColumnIndexOrThrow("name");

      while (cursor.moveToNext()) {
        String name = cursor.getString(nameColumnIndex);

        if (name.equals(column)) {
          return true;
        }
      }
    }

    return false;
  }

  /**
   * Returns an updated query and args pairing that will only update rows that would *actually*
   * change. In other words, if {@link SQLiteDatabase#update(String, ContentValues, String, String[])}
   * returns > 0, then you know something *actually* changed.
   */
  public static @NonNull Pair<String, String[]> buildTrueUpdateQuery(@NonNull String selection,
                                                                     @NonNull String[] args,
                                                                     @NonNull ContentValues contentValues)
  {
    StringBuilder                  qualifier = new StringBuilder();
    Set<Map.Entry<String, Object>> valueSet  = contentValues.valueSet();
    List<String>                   fullArgs  = new ArrayList<>(args.length + valueSet.size());

    fullArgs.addAll(Arrays.asList(args));

    int i = 0;

    for (Map.Entry<String, Object> entry : valueSet) {
      if (entry.getValue() != null) {
        qualifier.append(entry.getKey()).append(" != ? OR ").append(entry.getKey()).append(" IS NULL");
        fullArgs.add(String.valueOf(entry.getValue()));
      } else {
        qualifier.append(entry.getKey()).append(" NOT NULL");
      }

      if (i != valueSet.size() - 1) {
        qualifier.append(" OR ");
      }

      i++;
    }

    return new Pair<>("(" + selection + ") AND (" + qualifier + ")", fullArgs.toArray(new String[0]));
  }
}
