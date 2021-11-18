package org.thoughtcrime.securesms.util;

import android.content.Context;

import org.thoughtcrime.securesms.R;

import java.util.concurrent.TimeUnit;

public class ExpirationUtil {

  public static String getExpirationDisplayValue(Context context, int expirationTime) {
    if (expirationTime <= 0) {
      return context.getString(R.string.expiration_off);
    } else if (expirationTime < TimeUnit.MINUTES.toSeconds(1)) {
      return context.getResources().getQuantityString(R.plurals.expiration_seconds, expirationTime, expirationTime);
    } else if (expirationTime < TimeUnit.HOURS.toSeconds(1)) {
      int minutes = expirationTime / (int)TimeUnit.MINUTES.toSeconds(1);
      return context.getResources().getQuantityString(R.plurals.expiration_minutes, minutes, minutes);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(1)) {
      int hours = expirationTime / (int)TimeUnit.HOURS.toSeconds(1);
      return context.getResources().getQuantityString(R.plurals.expiration_hours, hours, hours);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(7)) {
      int days = expirationTime / (int)TimeUnit.DAYS.toSeconds(1);
      return context.getResources().getQuantityString(R.plurals.expiration_days, days, days);
    } else if (expirationTime < (int)TimeUnit.DAYS.toSeconds(30)) {
      int weeks = expirationTime / (int)TimeUnit.DAYS.toSeconds(7);
      return context.getResources().getQuantityString(R.plurals.expiration_weeks, weeks, weeks);
    } else if(expirationTime < (int) TimeUnit.DAYS.toSeconds(365)) {
      int months = expirationTime / (int)TimeUnit.DAYS.toSeconds(30);
      return context.getResources().getQuantityString(R.plurals.expiration_months, months, months);
    } else {
      int years = expirationTime / (int)TimeUnit.DAYS.toSeconds(365);
      return context.getResources().getQuantityString(R.plurals.expiration_years, years, years);
    }
  }

  public static String getExpirationAbbreviatedDisplayValue(Context context, int expirationTime) {
    if (expirationTime < TimeUnit.MINUTES.toSeconds(1)) {
      return context.getResources().getString(R.string.expiration_seconds_abbreviated, expirationTime);
    } else if (expirationTime < TimeUnit.HOURS.toSeconds(1)) {
      int minutes = expirationTime / (int)TimeUnit.MINUTES.toSeconds(1);
      return context.getResources().getString(R.string.expiration_minutes_abbreviated, minutes);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(1)) {
      int hours = expirationTime / (int)TimeUnit.HOURS.toSeconds(1);
      return context.getResources().getString(R.string.expiration_hours_abbreviated, hours);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(7)) {
      int days = expirationTime / (int)TimeUnit.DAYS.toSeconds(1);
      return context.getResources().getString(R.string.expiration_days_abbreviated, days);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(30)) {
      int weeks = expirationTime / (int)TimeUnit.DAYS.toSeconds(7);
      return context.getResources().getString(R.string.expiration_weeks_abbreviated, weeks);
    } else if (expirationTime < TimeUnit.DAYS.toSeconds(365)) {
      int months = expirationTime / (int)TimeUnit.DAYS.toSeconds(30);
      return context.getResources().getString(R.string.expiration_months_abbreviated, months);
    } else {
      int years = expirationTime / (int) TimeUnit.DAYS.toSeconds(365);
      return context.getResources().getString(R.string.expiration_years_abbreviated, years);
    }
  }


}
