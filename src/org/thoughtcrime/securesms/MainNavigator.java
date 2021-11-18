package org.thoughtcrime.securesms;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import org.thoughtcrime.securesms.conversation.ConversationActivity;
import org.thoughtcrime.securesms.conversationlist.ConversationListArchiveFragment;
import org.thoughtcrime.securesms.conversationlist.ConversationListFragment;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.vicon.ConversationListRoomFragment;
import org.whispersystems.signalservice.api.profiles.AccountTestToken;
import org.whispersystems.signalservice.api.profiles.ProfileTokenAndEndPoint;

public class MainNavigator {

  private final MainActivity activity;
  private int statusBarColorListFragment;

  public MainNavigator(@NonNull MainActivity activity) {
    this.activity = activity;
  }

  public static MainNavigator get(@NonNull Activity activity) {
    if (!(activity instanceof MainActivity)) {
      throw new IllegalArgumentException("Activity must be an instance of MainActivity!");
    }

    return ((MainActivity) activity).getNavigator();
  }

  public void onCreate(@Nullable Bundle savedInstanceState) {
    if (savedInstanceState != null) {
      return;
    }

    getFragmentManager().beginTransaction()
                        .add(R.id.fragment_container, ConversationListFragment.newInstance())
                        .commit();
  }

  /**
   * @return True if the back pressed was handled in our own custom way, false if it should be given
   *         to the system to do the default behavior.
   */
  public boolean onBackPressed() {
    Fragment fragment = getFragmentManager().findFragmentById(R.id.fragment_container);
    activity.setStatusBarColor(statusBarColorListFragment);
    if (fragment instanceof BackHandler) {
      return ((BackHandler) fragment).onBackPressed();
    }

    return false;
  }

  public void goToConversation(@NonNull RecipientId recipientId, long threadId, int distributionType, long lastSeen, int startingPosition) {
    Intent intent = ConversationActivity.buildIntent(activity, recipientId, threadId, distributionType, lastSeen, startingPosition);

    activity.startActivity(intent);
    activity.overridePendingTransition(R.anim.slide_from_end, R.anim.fade_scale_out);
  }

  public void goToAppSettings() {
    Intent intent = new Intent(activity, ApplicationPreferencesActivity.class);
    activity.startActivity(intent);
  }
  public void setStatusBarColorListFragment(int statusBarColorListFragment) {        this.statusBarColorListFragment = statusBarColorListFragment;    }
  public void goToAppListRoom(ProfileTokenAndEndPoint profileTokenAndEndPoint , AccountTestToken accountTestToken, int statusBarColor) {
    setStatusBarColorListFragment(statusBarColor);
    activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
              ConversationListRoomFragment fragmentbundle = new ConversationListRoomFragment();
              Bundle bundlefragment = new Bundle();
              bundlefragment.putString(ConversationListRoomFragment.END_POINT, profileTokenAndEndPoint.getEndpointUrl());
              bundlefragment.putString(ConversationListRoomFragment.RESULT_END_POINT, String.valueOf(profileTokenAndEndPoint.isResult()));
              bundlefragment.putString(ConversationListRoomFragment.ERROR_MESSAGE_END_POINT, profileTokenAndEndPoint.getErrorMessage());
              bundlefragment.putString(ConversationListRoomFragment.AUTH_TOKEN, accountTestToken.getAuthToken());
              bundlefragment.putString(ConversationListRoomFragment.USER_TIME, accountTestToken.getUserTime());
              bundlefragment.putString(ConversationListRoomFragment.RESULT_TOKEN, String.valueOf(accountTestToken.isResult()));
              bundlefragment.putString(ConversationListRoomFragment.ERROR_MESSAGE_TOKEN, accountTestToken.getErrorMessage());
              fragmentbundle.setArguments(bundlefragment);

              fragmentbundle.setStatusBarColor(statusBarColor);

              getFragmentManager().beginTransaction()
                      .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                      .replace(R.id.fragment_container, fragmentbundle)
                      .addToBackStack(null)
                      .commit();
            }
    });
  }


  public void goToArchiveList() {
    getFragmentManager().beginTransaction()
                        .setCustomAnimations(R.anim.slide_from_end, R.anim.slide_to_start, R.anim.slide_from_start, R.anim.slide_to_end)
                        .replace(R.id.fragment_container, ConversationListArchiveFragment.newInstance())
                        .addToBackStack(null)
                        .commit();
  }

  public void goToGroupCreation() {
    Intent intent = new Intent(activity, GroupCreateActivity.class);
    activity.startActivity(intent);
  }

  public void goToInvite() {
    Intent intent = new Intent(activity, InviteActivity.class);
    activity.startActivity(intent);
  }

  public void goToInsights() {
    InsightsLauncher.showInsightsDashboard(activity.getSupportFragmentManager());
  }

  private @NonNull FragmentManager getFragmentManager() {
    return activity.getSupportFragmentManager();
  }

  public interface BackHandler {
    /**
     * @return True if the back pressed was handled in our own custom way, false if it should be given
     *         to the system to do the default behavior.
     */
    boolean onBackPressed();
  }
}
