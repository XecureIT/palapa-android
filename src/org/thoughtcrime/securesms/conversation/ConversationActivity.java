/*
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.thoughtcrime.securesms.conversation;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Browser;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;
import androidx.core.view.MenuItemCompat;
import androidx.lifecycle.ViewModelProviders;

import com.abangfadli.shotwatch.ScreenshotData;
import com.abangfadli.shotwatch.ShotWatch;
import com.annimon.stream.Stream;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.thoughtcrime.securesms.ApplicationContext;
import org.thoughtcrime.securesms.BuildConfig;
import org.thoughtcrime.securesms.ExpirationDialog;
import org.thoughtcrime.securesms.GroupCreateActivity;
import org.thoughtcrime.securesms.GroupMembersDialog;
import org.thoughtcrime.securesms.MainActivity;
import org.thoughtcrime.securesms.MuteDialog;
import org.thoughtcrime.securesms.PassphraseRequiredActionBarActivity;
import org.thoughtcrime.securesms.PromptMmsActivity;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.RecipientPreferenceActivity;
import org.thoughtcrime.securesms.ShortcutLauncherActivity;
import org.thoughtcrime.securesms.TransportOption;
import org.thoughtcrime.securesms.VerifyIdentityActivity;
import org.thoughtcrime.securesms.attachments.Attachment;
import org.thoughtcrime.securesms.attachments.TombstoneAttachment;
import org.thoughtcrime.securesms.audio.AudioRecorder;
import org.thoughtcrime.securesms.audio.AudioSlidePlayer;
import org.thoughtcrime.securesms.color.MaterialColor;
import org.thoughtcrime.securesms.components.AnimatingToggle;
import org.thoughtcrime.securesms.components.AttachmentTypeSelector;
import org.thoughtcrime.securesms.components.ComposeText;
import org.thoughtcrime.securesms.components.ConversationSearchBottomBar;
import org.thoughtcrime.securesms.components.HidingLinearLayout;
import org.thoughtcrime.securesms.components.InputAwareLayout;
import org.thoughtcrime.securesms.components.InputPanel;
import org.thoughtcrime.securesms.components.KeyboardAwareLinearLayout.OnKeyboardShownListener;
import org.thoughtcrime.securesms.components.SendButton;
import org.thoughtcrime.securesms.components.TooltipPopup;
import org.thoughtcrime.securesms.components.emoji.EmojiKeyboardProvider;
import org.thoughtcrime.securesms.components.emoji.EmojiStrings;
import org.thoughtcrime.securesms.components.emoji.MediaKeyboard;
import org.thoughtcrime.securesms.components.identity.UntrustedSendDialog;
import org.thoughtcrime.securesms.components.identity.UnverifiedBannerView;
import org.thoughtcrime.securesms.components.identity.UnverifiedSendDialog;
import org.thoughtcrime.securesms.components.location.SignalPlace;
import org.thoughtcrime.securesms.components.reminder.ExpiredBuildReminder;
import org.thoughtcrime.securesms.components.reminder.Reminder;
import org.thoughtcrime.securesms.components.reminder.ReminderView;
import org.thoughtcrime.securesms.components.reminder.ServiceOutageReminder;
import org.thoughtcrime.securesms.components.reminder.UnauthorizedReminder;
import org.thoughtcrime.securesms.contacts.ContactAccessor;
import org.thoughtcrime.securesms.contacts.ContactAccessor.ContactData;
import org.thoughtcrime.securesms.contacts.sync.DirectoryHelper;
import org.thoughtcrime.securesms.contactshare.Contact;
import org.thoughtcrime.securesms.contactshare.ContactShareEditActivity;
import org.thoughtcrime.securesms.contactshare.ContactUtil;
import org.thoughtcrime.securesms.contactshare.SimpleTextWatcher;
import org.thoughtcrime.securesms.conversationlist.model.MessageResult;
import org.thoughtcrime.securesms.crypto.IdentityKeyParcelable;
import org.thoughtcrime.securesms.crypto.SecurityEvent;
import org.thoughtcrime.securesms.database.DatabaseFactory;
import org.thoughtcrime.securesms.database.DraftDatabase;
import org.thoughtcrime.securesms.database.DraftDatabase.Draft;
import org.thoughtcrime.securesms.database.DraftDatabase.Drafts;
import org.thoughtcrime.securesms.database.GroupDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase;
import org.thoughtcrime.securesms.database.IdentityDatabase.IdentityRecord;
import org.thoughtcrime.securesms.database.IdentityDatabase.VerifiedStatus;
import org.thoughtcrime.securesms.database.MessagingDatabase.MarkedMessageInfo;
import org.thoughtcrime.securesms.database.MmsSmsColumns.Types;
import org.thoughtcrime.securesms.database.RecipientDatabase;
import org.thoughtcrime.securesms.database.RecipientDatabase.RegisteredState;
import org.thoughtcrime.securesms.database.ThreadDatabase;
import org.thoughtcrime.securesms.database.identity.IdentityRecordList;
import org.thoughtcrime.securesms.database.model.MessageRecord;
import org.thoughtcrime.securesms.database.model.MmsMessageRecord;
import org.thoughtcrime.securesms.database.model.ReactionRecord;
import org.thoughtcrime.securesms.database.model.StickerRecord;
import org.thoughtcrime.securesms.dependencies.ApplicationDependencies;
import org.thoughtcrime.securesms.events.ReminderUpdateEvent;
import org.thoughtcrime.securesms.giph.ui.GiphyActivity;
import org.thoughtcrime.securesms.insights.InsightsLauncher;
import org.thoughtcrime.securesms.invites.InviteReminderModel;
import org.thoughtcrime.securesms.invites.InviteReminderRepository;
import org.thoughtcrime.securesms.jobs.RetrieveProfileJob;
import org.thoughtcrime.securesms.jobs.ServiceOutageDetectionJob;
import org.thoughtcrime.securesms.linkpreview.LinkPreview;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewRepository;
import org.thoughtcrime.securesms.linkpreview.LinkPreviewViewModel;
import org.thoughtcrime.securesms.logging.Log;
import org.thoughtcrime.securesms.maps.PlacePickerActivity;
import org.thoughtcrime.securesms.mediaoverview.MediaOverviewActivity;
import org.thoughtcrime.securesms.mediasend.Media;
import org.thoughtcrime.securesms.mediasend.MediaSendActivity;
import org.thoughtcrime.securesms.messagerequests.MessageRequestFragment;
import org.thoughtcrime.securesms.messagerequests.MessageRequestFragmentViewModel;
import org.thoughtcrime.securesms.mms.AttachmentManager;
import org.thoughtcrime.securesms.mms.AttachmentManager.MediaType;
import org.thoughtcrime.securesms.mms.AudioSlide;
import org.thoughtcrime.securesms.mms.GifSlide;
import org.thoughtcrime.securesms.mms.GlideApp;
import org.thoughtcrime.securesms.mms.GlideRequests;
import org.thoughtcrime.securesms.mms.ImageSlide;
import org.thoughtcrime.securesms.mms.LocationSlide;
import org.thoughtcrime.securesms.mms.MediaConstraints;
import org.thoughtcrime.securesms.mms.OutgoingExpirationUpdateMessage;
import org.thoughtcrime.securesms.mms.OutgoingGroupMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingMediaMessage;
import org.thoughtcrime.securesms.mms.OutgoingSecureMediaMessage;
import org.thoughtcrime.securesms.mms.QuoteId;
import org.thoughtcrime.securesms.mms.QuoteModel;
import org.thoughtcrime.securesms.mms.Slide;
import org.thoughtcrime.securesms.mms.SlideDeck;
import org.thoughtcrime.securesms.mms.StickerSlide;
import org.thoughtcrime.securesms.mms.TextSlide;
import org.thoughtcrime.securesms.mms.VideoSlide;
import org.thoughtcrime.securesms.notifications.MarkReadReceiver;
import org.thoughtcrime.securesms.notifications.MessageNotifier;
import org.thoughtcrime.securesms.notifications.NotificationChannels;
import org.thoughtcrime.securesms.permissions.Permissions;
import org.thoughtcrime.securesms.profiles.GroupShareProfileView;
import org.thoughtcrime.securesms.providers.BlobProvider;
import org.thoughtcrime.securesms.recipients.LiveRecipient;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientExporter;
import org.thoughtcrime.securesms.recipients.RecipientFormattingException;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.recipients.RecipientUtil;
import org.thoughtcrime.securesms.registration.RegistrationNavigationActivity;
import org.thoughtcrime.securesms.service.KeyCachingService;
import org.thoughtcrime.securesms.sms.MessageSender;
import org.thoughtcrime.securesms.sms.OutgoingEncryptedMessage;
import org.thoughtcrime.securesms.sms.OutgoingEndSessionMessage;
import org.thoughtcrime.securesms.sms.OutgoingTextMessage;
import org.thoughtcrime.securesms.stickers.StickerKeyboardProvider;
import org.thoughtcrime.securesms.stickers.StickerLocator;
import org.thoughtcrime.securesms.stickers.StickerManagementActivity;
import org.thoughtcrime.securesms.stickers.StickerPackInstallEvent;
import org.thoughtcrime.securesms.stickers.StickerSearchRepository;
import org.thoughtcrime.securesms.util.BitmapUtil;
import org.thoughtcrime.securesms.util.CharacterCalculator.CharacterState;
import org.thoughtcrime.securesms.util.CommunicationActions;
import org.thoughtcrime.securesms.util.Dialogs;
import org.thoughtcrime.securesms.util.DynamicDarkToolbarTheme;
import org.thoughtcrime.securesms.util.DynamicLanguage;
import org.thoughtcrime.securesms.util.DynamicTheme;
import org.thoughtcrime.securesms.util.ExpirationUtil;
import org.thoughtcrime.securesms.util.FeatureFlags;
import org.thoughtcrime.securesms.util.GroupUtil;
import org.thoughtcrime.securesms.util.IdentityUtil;
import org.thoughtcrime.securesms.util.MediaUtil;
import org.thoughtcrime.securesms.util.ServiceUtil;
import org.thoughtcrime.securesms.util.TextSecurePreferences;
import org.thoughtcrime.securesms.util.TextSecurePreferences.MediaKeyboardMode;
import org.thoughtcrime.securesms.util.Util;
import org.thoughtcrime.securesms.util.ViewUtil;
import org.thoughtcrime.securesms.util.concurrent.AssertedSuccessListener;
import org.thoughtcrime.securesms.util.concurrent.ListenableFuture;
import org.thoughtcrime.securesms.util.concurrent.SettableFuture;
import org.thoughtcrime.securesms.util.concurrent.SignalExecutors;
import org.thoughtcrime.securesms.util.concurrent.SimpleTask;
import org.thoughtcrime.securesms.util.views.Stub;
import org.whispersystems.libsignal.InvalidMessageException;
import org.whispersystems.libsignal.util.guava.Optional;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.thoughtcrime.securesms.TransportOption.Type;
import static org.thoughtcrime.securesms.database.GroupDatabase.GroupRecord;
import static org.whispersystems.libsignal.SessionCipher.SESSION_LOCK;

/**
 * Activity for displaying a message thread, as well as
 * composing/sending a new message into that thread.
 *
 * @author Moxie Marlinspike
 *
 */
@SuppressLint("StaticFieldLeak")
public class ConversationActivity extends PassphraseRequiredActionBarActivity
    implements ConversationFragment.ConversationFragmentListener,
               AttachmentManager.AttachmentListener,
               OnKeyboardShownListener,
               InputPanel.Listener,
               InputPanel.MediaListener,
               ComposeText.CursorPositionChangedListener,
               ConversationSearchBottomBar.EventListener,
               StickerKeyboardProvider.StickerEventListener
{
  private static final String TAG = ConversationActivity.class.getSimpleName();

  public static final String RECIPIENT_EXTRA         = "recipient_id";
  public static final String THREAD_ID_EXTRA         = "thread_id";
  public static final String IS_ARCHIVED_EXTRA       = "is_archived";
  public static final String TEXT_EXTRA              = "draft_text";
  public static final String MEDIA_EXTRA             = "media_list";
  public static final String STICKER_EXTRA           = "sticker_extra";
  public static final String DISTRIBUTION_TYPE_EXTRA = "distribution_type";
  public static final String LAST_SEEN_EXTRA         = "last_seen";
  public static final String STARTING_POSITION_EXTRA = "starting_position";

  private static final int PICK_GALLERY        = 1;
  private static final int PICK_DOCUMENT       = 2;
  private static final int PICK_AUDIO          = 3;
  private static final int PICK_CONTACT        = 4;
  private static final int GET_CONTACT_DETAILS = 5;
  private static final int GROUP_EDIT          = 6;
  private static final int TAKE_PHOTO          = 7;
  private static final int ADD_CONTACT         = 8;
  private static final int PICK_LOCATION       = 9;
  private static final int PICK_GIF            = 10;
  private static final int SMS_DEFAULT         = 11;
  private static final int MEDIA_SENDER        = 12;

  private   GlideRequests              glideRequests;
  protected ComposeText                composeText;
  private   AnimatingToggle            buttonToggle;
  private   SendButton                 sendButton;
  private   ImageButton                attachButton;
  protected ConversationTitleView      titleView;
  private   TextView                   charactersLeft;
  private   ConversationFragment       fragment;
  private   Button                     unblockButton;
  private   Button                     makeDefaultSmsButton;
  private   Button                     registerButton;
  private   InputAwareLayout           container;
  private   View                       composePanel;
  protected Stub<ReminderView>         reminderView;
  private   Stub<UnverifiedBannerView> unverifiedBannerView;
  private   Stub<GroupShareProfileView> groupShareProfileView;
  private   TypingStatusTextWatcher     typingTextWatcher;
  private   ConversationSearchBottomBar searchNav;
  private   MenuItem                    searchViewItem;
  private   FrameLayout                 messageRequestOverlay;
  private   ConversationReactionOverlay reactionOverlay;

  private   AttachmentTypeSelector attachmentTypeSelector;
  private   AttachmentManager      attachmentManager;
  private   AudioRecorder          audioRecorder;
  private   BroadcastReceiver      securityUpdateReceiver;
  private   Stub<MediaKeyboard>    emojiDrawerStub;
  protected HidingLinearLayout     quickAttachmentToggle;
  protected HidingLinearLayout     inlineAttachmentToggle;
  private   InputPanel             inputPanel;
  private   ShotWatch              shotWatch;

  private LinkPreviewViewModel         linkPreviewViewModel;
  private ConversationSearchViewModel  searchViewModel;
  private ConversationStickerViewModel stickerViewModel;
  private InviteReminderModel          inviteReminderModel;

  private LiveRecipient recipient;
  private long          threadId;
  private int           distributionType;
  private boolean       archived;
  private boolean       isSecureText;
  private boolean       isDefaultSms          = true;
  private boolean       isMmsEnabled          = true;
  private boolean       isSecurityInitialized = false;

  private final IdentityRecordList identityRecords = new IdentityRecordList();
  private final DynamicTheme       dynamicTheme    = new DynamicDarkToolbarTheme();
  private final DynamicLanguage    dynamicLanguage = new DynamicLanguage();

  public static @NonNull Intent buildIntent(@NonNull Context context,
                                            @NonNull RecipientId recipientId,
                                            long threadId,
                                            int distributionType,
                                            long lastSeen,
                                            int startingPosition)
  {
    Intent intent = new Intent(context, ConversationActivity.class);
    intent.putExtra(ConversationActivity.RECIPIENT_EXTRA, recipientId);
    intent.putExtra(ConversationActivity.THREAD_ID_EXTRA, threadId);
    intent.putExtra(ConversationActivity.DISTRIBUTION_TYPE_EXTRA, distributionType);
    intent.putExtra(ConversationActivity.LAST_SEEN_EXTRA, lastSeen);
    intent.putExtra(ConversationActivity.STARTING_POSITION_EXTRA, startingPosition);

    return intent;
  }

  @Override
  protected void onPreCreate() {
    dynamicTheme.onCreate(this);
    dynamicLanguage.onCreate(this);
  }

  @Override
  protected void onCreate(Bundle state, boolean ready) {
    RecipientId recipientId = getIntent().getParcelableExtra(RECIPIENT_EXTRA);

    if (recipientId == null) {
      Log.w(TAG, "[onCreate] Missing recipientId!");
      // TODO [greyson] Navigation
      startActivity(new Intent(this, MainActivity.class));
      finish();
      return;
    }


    setContentView(R.layout.conversation_activity);

    TypedArray typedArray = obtainStyledAttributes(new int[] {R.attr.conversation_background});
    int color = typedArray.getColor(0, Color.WHITE);
    typedArray.recycle();

    getWindow().getDecorView().setBackgroundColor(color);

    fragment = initFragment(R.id.fragment_content, new ConversationFragment(), dynamicLanguage.getCurrentLocale());

    initializeReceivers();
    initializeActionBar();
    initializeViews();
    initializeResources();
    initializeLinkPreviewObserver();
    initializeSearchObserver();
    initializeStickerObserver();
    initializeSecurity(recipient.get().isRegistered(), isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeProfiles();
        initializeDraft().addListener(new AssertedSuccessListener<Boolean>() {
          @Override
          public void onSuccess(Boolean loadedDraft) {
            if (loadedDraft != null && loadedDraft) {
              Log.i(TAG, "Finished loading draft");
              Util.runOnMain(() -> {
                if (fragment != null && fragment.isResumed()) {
                  fragment.moveToLastSeen();
                } else {
                  Log.w(TAG, "Wanted to move to the last seen position, but the fragment was in an invalid state");
                }
              });
            }

            if (TextSecurePreferences.isTypingIndicatorsEnabled(ConversationActivity.this)) {
              composeText.addTextChangedListener(typingTextWatcher);
            }
            composeText.setSelection(composeText.length(), composeText.length());
          }
        });
      }
    });
    initializeInsightObserver();
    initializeScreenshotEvent();
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    Log.i(TAG, "onNewIntent()");
    
    if (isFinishing()) {
      Log.w(TAG, "Activity is finishing...");
      return;
    }

    if (!Util.isEmpty(composeText) || attachmentManager.isAttachmentPresent()) {
      saveDraft();
      attachmentManager.clear(glideRequests, false);
      silentlySetComposeText("");
    }

    RecipientId recipientId = intent.getParcelableExtra(RECIPIENT_EXTRA);

    if (recipientId == null) {
      Log.w(TAG, "[onNewIntent] Missing recipientId!");
      // TODO [greyson] Navigation
      startActivity(new Intent(this, MainActivity.class));
      finish();
      return;
    }

    setIntent(intent);
    initializeResources();
    initializeSecurity(recipient.get().isRegistered(), isDefaultSms).addListener(new AssertedSuccessListener<Boolean>() {
      @Override
      public void onSuccess(Boolean result) {
        initializeDraft();
      }
    });

    if (fragment != null) {
      fragment.onNewIntent();
    }

    searchNav.setVisibility(View.GONE);
  }

  @Override
  protected void onResume() {
    super.onResume();
    dynamicTheme.onResume(this);
    dynamicLanguage.onResume(this);

    EventBus.getDefault().register(this);
    initializeEnabledCheck();
    initializeMmsEnabledCheck();
    initializeIdentityRecords();
    composeText.setTransport(sendButton.getSelectedTransport());

    Recipient recipientSnapshot = recipient.get();

    titleView.setTitle(glideRequests, recipientSnapshot);
    setActionBarColor(recipientSnapshot.getColor());
    setBlockedUserState(recipientSnapshot, isSecureText, isDefaultSms);
    setGroupShareProfileReminder(recipientSnapshot);
    calculateCharactersRemaining();

    MessageNotifier.setVisibleThread(threadId);
    markThreadAsRead();
    shotWatch.register();
  }

  @Override
  protected void onPause() {
    super.onPause();
    MessageNotifier.setVisibleThread(-1L);
    if (isFinishing()) overridePendingTransition(R.anim.fade_scale_in, R.anim.slide_to_end);
    inputPanel.onPause();

    fragment.setLastSeen(System.currentTimeMillis());
    markLastSeen();
    AudioSlidePlayer.stopAll();
    EventBus.getDefault().unregister(this);
    shotWatch.unregister();
  }

  @Override
  protected void onStop() {
    super.onStop();
    EventBus.getDefault().unregister(this);
  }

  @Override
  public void onConfigurationChanged(Configuration newConfig) {
    Log.i(TAG, "onConfigurationChanged(" + newConfig.orientation + ")");
    super.onConfigurationChanged(newConfig);
    composeText.setTransport(sendButton.getSelectedTransport());

    if (emojiDrawerStub.resolved() && container.getCurrentInput() == emojiDrawerStub.get()) {
      container.hideAttachedInput(true);
    }
  }

  @Override
  protected void onDestroy() {
    saveDraft();
    if (securityUpdateReceiver != null)  unregisterReceiver(securityUpdateReceiver);
    super.onDestroy();
  }

  @Override
  public boolean dispatchTouchEvent(MotionEvent ev) {
    return reactionOverlay.applyTouchEvent(ev) || super.dispatchTouchEvent(ev);
  }

  @Override
  public void onActivityResult(final int reqCode, int resultCode, Intent data) {
    Log.i(TAG, "onActivityResult called: " + reqCode + ", " + resultCode + " , " + data);
    super.onActivityResult(reqCode, resultCode, data);

    if ((data == null && reqCode != TAKE_PHOTO && reqCode != SMS_DEFAULT) ||
        (resultCode != RESULT_OK && reqCode != SMS_DEFAULT))
    {
      updateLinkPreviewState();
      return;
    }

    switch (reqCode) {
    case PICK_DOCUMENT:
      setMedia(data.getData(), MediaType.DOCUMENT);
      break;
    case PICK_AUDIO:
      setMedia(data.getData(), MediaType.AUDIO);
      break;
    case PICK_CONTACT:
      if (isSecureText && !isSmsForced()) {
        openContactShareEditor(data.getData());
      } else {
        addAttachmentContactInfo(data.getData());
      }
      break;
    case GET_CONTACT_DETAILS:
      sendSharedContact(data.getParcelableArrayListExtra(ContactShareEditActivity.KEY_CONTACTS));
      break;
    case GROUP_EDIT:
      Recipient recipientSnapshot = recipient.get();

      onRecipientChanged(recipientSnapshot);
      titleView.setTitle(glideRequests, recipientSnapshot);
      NotificationChannels.updateContactChannelName(this, recipientSnapshot);
      setBlockedUserState(recipientSnapshot, isSecureText, isDefaultSms);
      supportInvalidateOptionsMenu();
      break;
    case TAKE_PHOTO:
      handleImageFromDeviceCameraApp();
      break;
    case ADD_CONTACT:
      onRecipientChanged(recipient.get());
      fragment.reloadList();
      break;
    case PICK_LOCATION:
      SignalPlace place = new SignalPlace(PlacePickerActivity.addressFromData(data));
      attachmentManager.setLocation(place, getCurrentMediaConstraints());
      break;
    case PICK_GIF:
      setMedia(data.getData(),
               MediaType.GIF,
               data.getIntExtra(GiphyActivity.EXTRA_WIDTH, 0),
               data.getIntExtra(GiphyActivity.EXTRA_HEIGHT, 0));
      break;
    case SMS_DEFAULT:
      initializeSecurity(isSecureText, isDefaultSms);
      break;
    case MEDIA_SENDER:
      long            expiresIn      = recipient.get().getExpireMessages() * 1000L;
      int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      boolean         initiating     = threadId == -1;
      TransportOption transport      = data.getParcelableExtra(MediaSendActivity.EXTRA_TRANSPORT);
      String          message        = data.getStringExtra(MediaSendActivity.EXTRA_MESSAGE);
      boolean         viewOnce       = data.getBooleanExtra(MediaSendActivity.EXTRA_VIEW_ONCE, false);
      QuoteModel      quote          = viewOnce ? null : inputPanel.getQuote().orNull();
      SlideDeck       slideDeck      = new SlideDeck();

      if (transport == null) {
        throw new IllegalStateException("Received a null transport from the MediaSendActivity.");
      }

      sendButton.setTransport(transport);

      List<Media> mediaList = data.getParcelableArrayListExtra(MediaSendActivity.EXTRA_MEDIA);

      for (Media mediaItem : mediaList) {
        if (MediaUtil.isVideoType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new VideoSlide(this, mediaItem.getUri(), 0, mediaItem.getCaption().orNull()));
        } else if (MediaUtil.isGif(mediaItem.getMimeType())) {
          slideDeck.addSlide(new GifSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull()));
        } else if (MediaUtil.isImageType(mediaItem.getMimeType())) {
          slideDeck.addSlide(new ImageSlide(this, mediaItem.getUri(), 0, mediaItem.getWidth(), mediaItem.getHeight(), mediaItem.getCaption().orNull(), null));
        } else {
          Log.w(TAG, "Asked to send an unexpected mimeType: '" + mediaItem.getMimeType() + "'. Skipping.");
        }
      }

      final Context context = ConversationActivity.this.getApplicationContext();

      sendMediaMessage(transport.isSms(),
                       message,
                       slideDeck,
                       quote,
                       Collections.emptyList(),
                       Collections.emptyList(),
                       expiresIn,
                       viewOnce,
                       subscriptionId,
                       initiating,
                       true).addListener(new AssertedSuccessListener<Void>() {
        @Override
        public void onSuccess(Void result) {
          AsyncTask.THREAD_POOL_EXECUTOR.execute(() -> {
            Stream.of(slideDeck.getSlides())
                  .map(Slide::getUri)
                  .withoutNulls()
                  .filter(BlobProvider::isAuthority)
                  .forEach(uri -> BlobProvider.getInstance().delete(context, uri));
          });
        }
      });

      break;
    }
  }

  private void handleImageFromDeviceCameraApp() {
    if (attachmentManager.getCaptureUri() == null) {
      Log.w(TAG, "No image available.");
      return;
    }

    try {
      Uri mediaUri = BlobProvider.getInstance()
                                 .forData(getContentResolver().openInputStream(attachmentManager.getCaptureUri()), 0L)
                                 .withMimeType(MediaUtil.IMAGE_JPEG)
                                 .createForSingleSessionOnDisk(this);

      getContentResolver().delete(attachmentManager.getCaptureUri(), null, null);

      setMedia(mediaUri, MediaType.IMAGE);
    } catch (IOException ioe) {
      Log.w(TAG, "Could not handle public image", ioe);
    }
  }

  @Override
  public void startActivity(Intent intent) {
    if (intent.getStringExtra(Browser.EXTRA_APPLICATION_ID) != null) {
      intent.removeExtra(Browser.EXTRA_APPLICATION_ID);
    }

    try {
      super.startActivity(intent);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
      Toast.makeText(this, R.string.ConversationActivity_there_is_no_app_available_to_handle_this_link_on_your_device, Toast.LENGTH_LONG).show();
    }
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    MenuInflater inflater = this.getMenuInflater();
    menu.clear();

    if (isSecureText) {
      if (recipient.get().getExpireMessages() > 0) {
        inflater.inflate(R.menu.conversation_expiring_on, menu);
        titleView.showExpiring(recipient);
      } else {
        inflater.inflate(R.menu.conversation_expiring_off, menu);
        titleView.clearExpiring();
      }
    }

    if (isSingleConversation()) {
      if (isSecureText) inflater.inflate(R.menu.conversation_callable_secure, menu);
      else              inflater.inflate(R.menu.conversation_callable_insecure, menu);
    } else if (isGroupConversation()) {
      inflater.inflate(R.menu.conversation_group_options, menu);

      if (!isPushGroupConversation()) {
        inflater.inflate(R.menu.conversation_mms_group_options, menu);
        if (distributionType == ThreadDatabase.DistributionTypes.BROADCAST) {
          menu.findItem(R.id.menu_distribution_broadcast).setChecked(true);
        } else {
          menu.findItem(R.id.menu_distribution_conversation).setChecked(true);
        }
      } else if (isActiveGroup()) {
        inflater.inflate(R.menu.conversation_push_group_options, menu);
      }
    }

    inflater.inflate(R.menu.conversation, menu);

    if (isSingleConversation() && isSecureText) {
      inflater.inflate(R.menu.conversation_secure, menu);
    } else if (isSingleConversation()) {
      inflater.inflate(R.menu.conversation_insecure, menu);
    }

    if (recipient != null && recipient.get().isMuted()) inflater.inflate(R.menu.conversation_muted, menu);
    else                                                inflater.inflate(R.menu.conversation_unmuted, menu);

    if (isSingleConversation() && getRecipient().getContactUri() == null) {
      inflater.inflate(R.menu.conversation_add_to_contacts, menu);
    }

    if (recipient != null && recipient.get().isLocalNumber()) {
      if (isSecureText) {
        menu.findItem(R.id.menu_call_secure).setVisible(false);
        menu.findItem(R.id.menu_video_secure).setVisible(false);
      } else {
        menu.findItem(R.id.menu_call_insecure).setVisible(false);
      }

      MenuItem muteItem = menu.findItem(R.id.menu_mute_notifications);

      if (muteItem != null) {
        muteItem.setVisible(false);
      }
    }

    searchViewItem = menu.findItem(R.id.menu_search);

    SearchView                     searchView    = (SearchView) searchViewItem.getActionView();
    SearchView.OnQueryTextListener queryListener = new SearchView.OnQueryTextListener() {
      @Override
      public boolean onQueryTextSubmit(String query) {
        searchViewModel.onQueryUpdated(query, threadId, true);
        searchNav.showLoading();
        fragment.onSearchQueryUpdated(query);
        return true;
      }

      @Override
      public boolean onQueryTextChange(String query) {
        searchViewModel.onQueryUpdated(query, threadId, false);
        searchNav.showLoading();
        fragment.onSearchQueryUpdated(query);
        return true;
      }
    };

    searchViewItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
      @Override
      public boolean onMenuItemActionExpand(MenuItem item) {
        searchView.setOnQueryTextListener(queryListener);
        searchViewModel.onSearchOpened();
        searchNav.setVisibility(View.VISIBLE);
        searchNav.setData(0, 0);
        inputPanel.setVisibility(View.GONE);

        for (int i = 0; i < menu.size(); i++) {
          if (!menu.getItem(i).equals(searchViewItem)) {
            menu.getItem(i).setVisible(false);
          }
        }
        return true;
      }

      @Override
      public boolean onMenuItemActionCollapse(MenuItem item) {
        searchView.setOnQueryTextListener(null);
        searchViewModel.onSearchClosed();
        searchNav.setVisibility(View.GONE);
        inputPanel.setVisibility(View.VISIBLE);
        fragment.onSearchQueryUpdated(null);
        invalidateOptionsMenu();
        return true;
      }
    });

    super.onPrepareOptionsMenu(menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    super.onOptionsItemSelected(item);
    switch (item.getItemId()) {
    case R.id.menu_call_secure:               handleDial(getRecipient(), true);                  return true;
    case R.id.menu_video_secure:              handleVideo(getRecipient());                       return true;
    case R.id.menu_call_insecure:             handleDial(getRecipient(), false);                 return true;
    case R.id.menu_view_media:                handleViewMedia();                                 return true;
    case R.id.menu_add_shortcut:              handleAddShortcut();                               return true;
    case R.id.menu_search:                    handleSearch();                                    return true;
    case R.id.menu_add_to_contacts:           handleAddToContacts();                             return true;
    case R.id.menu_reset_secure_session:      handleResetSecureSession();                        return true;
    case R.id.menu_group_recipients:          handleDisplayGroupRecipients();                    return true;
    case R.id.menu_distribution_broadcast:    handleDistributionBroadcastEnabled(item);          return true;
    case R.id.menu_distribution_conversation: handleDistributionConversationEnabled(item);       return true;
    case R.id.menu_edit_group:                handleEditPushGroup();                             return true;
    case R.id.menu_leave:                     handleLeavePushGroup();                            return true;
    case R.id.menu_invite:                    handleInviteLink();                                return true;
    case R.id.menu_mute_notifications:        handleMuteNotifications();                         return true;
    case R.id.menu_unmute_notifications:      handleUnmuteNotifications();                       return true;
    case R.id.menu_conversation_settings:     handleConversationSettings();                      return true;
    case R.id.menu_expiring_messages_off:
    case R.id.menu_expiring_messages:         handleSelectMessageExpiration();                   return true;
    case android.R.id.home:                   onBackPressed();                                   return true;
    }

    return false;
  }

  @Override
  public void onBackPressed() {
    Log.d(TAG, "onBackPressed()");
    if (container.isInputOpen()) container.hideCurrentInput(composeText);
    else                         super.onBackPressed();
  }

  @Override
  public void onKeyboardShown() {
    inputPanel.onKeyboardShown();
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onEvent(ReminderUpdateEvent event) {
    updateReminders();
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    Permissions.onRequestPermissionsResult(this, requestCode, permissions, grantResults);
  }

  //////// Event Handlers

  private void handleSelectMessageExpiration() {
    if (isPushGroupConversation() && !isActiveGroup()) {
      return;
    }

    //noinspection CodeBlock2Expr
    ExpirationDialog.show(this, recipient.get().getExpireMessages(), expirationTime -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getRecipientDatabase(ConversationActivity.this).setExpireMessages(recipient.getId(), expirationTime);
          OutgoingExpirationUpdateMessage outgoingMessage = new OutgoingExpirationUpdateMessage(getRecipient(), System.currentTimeMillis(), expirationTime * 1000L);
          MessageSender.send(ConversationActivity.this, outgoingMessage, threadId, false, null);

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          invalidateOptionsMenu();
          if (fragment != null) fragment.setLastSeen(0);
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
  }

  private void handleMuteNotifications() {
    MuteDialog.show(this, until -> {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                         .setMuted(recipient.getId(), until);

          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    });
  }

  private void handleConversationSettings() {
    Intent intent = new Intent(ConversationActivity.this, RecipientPreferenceActivity.class);
    intent.putExtra(RecipientPreferenceActivity.RECIPIENT_ID, recipient.getId());
    intent.putExtra(RecipientPreferenceActivity.CAN_HAVE_SAFETY_NUMBER_EXTRA,
                    isSecureText && !isSelfConversation());

    startActivitySceneTransition(intent, titleView.findViewById(R.id.contact_photo_image), "avatar");
  }

  private void handleUnmuteNotifications() {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        DatabaseFactory.getRecipientDatabase(ConversationActivity.this)
                       .setMuted(recipient.getId(), 0);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private void handleUnblock() {
    int titleRes = R.string.ConversationActivity_unblock_this_contact_question;
    int bodyRes  = R.string.ConversationActivity_you_will_once_again_be_able_to_receive_messages_and_calls_from_this_contact;

    if (recipient.get().isGroup()) {
      titleRes = R.string.ConversationActivity_unblock_this_group_question;
      bodyRes  = R.string.ConversationActivity_unblock_this_group_description;
    }

    //noinspection CodeBlock2Expr
    new AlertDialog.Builder(this)
                   .setTitle(titleRes)
                   .setMessage(bodyRes)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(R.string.ConversationActivity_unblock, (dialog, which) -> {
                     SimpleTask.run(() -> {
                       RecipientUtil.unblock(ConversationActivity.this, recipient.get());
                       return RecipientUtil.isRecipientMessageRequestAccepted(ConversationActivity.this, recipient.get());
                     }, messageRequestAccepted -> {
                       if (!messageRequestAccepted) {
                         onMessageRequest();
                       }
                     });
                   }).show();
  }

  @TargetApi(Build.VERSION_CODES.KITKAT)
  private void handleMakeDefaultSms() {
    Intent intent = new Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT);
    intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, getPackageName());
    startActivityForResult(intent, SMS_DEFAULT);
  }

  private void handleRegisterForSignal() {
    startActivity(RegistrationNavigationActivity.newIntentForReRegistration(this));
  }

  private void handleInviteLink() {
    String inviteText = getString(R.string.ConversationActivity_lets_switch_to_signal, getString(R.string.install_url));

    if (isDefaultSms) {
      composeText.appendInvite(inviteText);
    } else {
      Intent intent = new Intent(Intent.ACTION_SENDTO);
      intent.setData(Uri.parse("smsto:" + recipient.get().requireSmsAddress()));
      intent.putExtra("sms_body", inviteText);
      intent.putExtra(Intent.EXTRA_TEXT, inviteText);
      startActivity(intent);
    }
  }

  private void handleResetSecureSession() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(R.string.ConversationActivity_reset_secure_session_question);
    builder.setIconAttribute(R.attr.dialog_alert_icon);
    builder.setCancelable(true);
    builder.setMessage(R.string.ConversationActivity_this_may_help_if_youre_having_encryption_problems);
    builder.setPositiveButton(R.string.ConversationActivity_reset, (dialog, which) -> {
      if (isSingleConversation()) {
        final Context context = getApplicationContext();

        OutgoingEndSessionMessage endSessionMessage =
            new OutgoingEndSessionMessage(new OutgoingTextMessage(getRecipient(), "TERMINATE", 0, -1));

        new AsyncTask<OutgoingEndSessionMessage, Void, Long>() {
          @Override
          protected Long doInBackground(OutgoingEndSessionMessage... messages) {
            return MessageSender.send(context, messages[0], threadId, false, null);
          }

          @Override
          protected void onPostExecute(Long result) {
            sendComplete(result);
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, endSessionMessage);
      }
    });
    builder.setNegativeButton(android.R.string.cancel, null);
    builder.show();
  }

  private void handleViewMedia() {
    startActivity(MediaOverviewActivity.forThread(this, threadId));
  }

  private void handleAddShortcut() {
    Log.i(TAG, "Creating home screen shortcut for recipient " + recipient.get().getId());

    new AsyncTask<Void, Void, IconCompat>() {

      @Override
      protected IconCompat doInBackground(Void... voids) {
        Context    context = getApplicationContext();
        IconCompat icon    = null;

        if (recipient.get().getContactPhoto() != null) {
          try {
            Bitmap bitmap = BitmapFactory.decodeStream(recipient.get().getContactPhoto().openInputStream(context));
            bitmap = BitmapUtil.createScaledBitmap(bitmap, 300, 300);
            icon   = IconCompat.createWithAdaptiveBitmap(bitmap);
          } catch (IOException e) {
            Log.w(TAG, "Failed to decode contact photo during shortcut creation. Falling back to generic icon.", e);
          }
        }

        if (icon == null) {
          icon = IconCompat.createWithResource(context, recipient.get().isGroup() ? R.mipmap.ic_group_shortcut
                                                                                           : R.mipmap.ic_person_shortcut);
        }

        return icon;
      }

      @Override
      protected void onPostExecute(IconCompat icon) {
        Context context  = getApplicationContext();
        String  name     = recipient.get().getDisplayName(ConversationActivity.this);

        ShortcutInfoCompat shortcutInfo = new ShortcutInfoCompat.Builder(context, recipient.get().getId().serialize() + '-' + System.currentTimeMillis())
                                                                .setShortLabel(name)
                                                                .setIcon(icon)
                                                                .setIntent(ShortcutLauncherActivity.createIntent(context, recipient.getId()))
                                                                .build();

        if (ShortcutManagerCompat.requestPinShortcut(context, shortcutInfo, null)) {
          Toast.makeText(context, getString(R.string.ConversationActivity_added_to_home_screen), Toast.LENGTH_LONG).show();
        }
      }
    }.execute();
  }

  private void handleSearch() {
    searchViewModel.onSearchOpened();
  }

  private void handleLeavePushGroup() {
    if (getRecipient() == null) {
      Toast.makeText(this, getString(R.string.ConversationActivity_invalid_recipient),
                     Toast.LENGTH_LONG).show();
      return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle(getString(R.string.ConversationActivity_leave_group));
    builder.setIconAttribute(R.attr.dialog_info_icon);
    builder.setCancelable(true);
    builder.setMessage(getString(R.string.ConversationActivity_are_you_sure_you_want_to_leave_this_group));
    builder.setPositiveButton(R.string.yes, (dialog, which) -> {
      Recipient                           groupRecipient = getRecipient();
      long                                threadId       = DatabaseFactory.getThreadDatabase(this).getThreadIdFor(groupRecipient);
      Optional<OutgoingGroupMediaMessage> leaveMessage   = GroupUtil.createGroupLeaveMessage(this, groupRecipient);

      if (threadId != -1 && leaveMessage.isPresent()) {
        MessageSender.send(this, leaveMessage.get(), threadId, false, null);

        GroupDatabase groupDatabase = DatabaseFactory.getGroupDatabase(this);
        String        groupId       = groupRecipient.requireGroupId();
        groupDatabase.setActive(groupId, false);
        groupDatabase.remove(groupId, Recipient.self().getId());
        groupDatabase.removeAdmin(groupId, Recipient.self().getId());

        initializeEnabledCheck();
      } else {
        Toast.makeText(this, R.string.ConversationActivity_error_leaving_group, Toast.LENGTH_LONG).show();
      }
    });

    builder.setNegativeButton(R.string.no, null);
    builder.show();
  }

  private void handleEditPushGroup() {
    Intent intent = new Intent(ConversationActivity.this, GroupCreateActivity.class);
    intent.putExtra(GroupCreateActivity.GROUP_ID_EXTRA, recipient.get().requireGroupId());
    startActivityForResult(intent, GROUP_EDIT);
  }

  private void handleDistributionBroadcastEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.BROADCAST;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.BROADCAST);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDistributionConversationEnabled(MenuItem item) {
    distributionType = ThreadDatabase.DistributionTypes.CONVERSATION;
    item.setChecked(true);

    if (threadId != -1) {
      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          DatabaseFactory.getThreadDatabase(ConversationActivity.this)
                         .setDistributionType(threadId, ThreadDatabase.DistributionTypes.CONVERSATION);
          return null;
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private void handleDial(final Recipient recipient, boolean isSecure) {
    if (recipient == null) return;

    if (isSecure) {
      CommunicationActions.startVoiceCall(this, recipient);
    } else {
      try {
        Intent dialIntent = new Intent(Intent.ACTION_DIAL,
                                       Uri.parse("tel:" + recipient.requireSmsAddress()));
        startActivity(dialIntent);
      } catch (ActivityNotFoundException anfe) {
        Log.w(TAG, anfe);
        Dialogs.showAlertDialog(this,
                                getString(R.string.ConversationActivity_calls_not_supported),
                                getString(R.string.ConversationActivity_this_device_does_not_appear_to_support_dial_actions));
      }
    }
  }

  private void handleVideo(final Recipient recipient) {
    if (recipient == null) return;

    CommunicationActions.startVideoCall(this, recipient);
  }

  private void handleDisplayGroupRecipients() {
    new GroupMembersDialog(this, getRecipient()).display();
  }

  private void handleAddToContacts() {
    if (recipient.get().isGroup()) return;

    try {
      startActivityForResult(RecipientExporter.export(recipient.get()).asAddContactIntent(), ADD_CONTACT);
    } catch (ActivityNotFoundException e) {
      Log.w(TAG, e);
    }
  }

  private boolean handleDisplayQuickContact() {
    if (recipient.get().isGroup()) return false;

    if (recipient.get().getContactUri() != null) {
      ContactsContract.QuickContact.showQuickContact(ConversationActivity.this, titleView, recipient.get().getContactUri(), ContactsContract.QuickContact.MODE_LARGE, null);
    } else {
      handleAddToContacts();
    }

    return true;
  }

  private void handleAddAttachment() {
    if (this.isMmsEnabled || isSecureText) {
      if (attachmentTypeSelector == null) {
        attachmentTypeSelector = new AttachmentTypeSelector(this, getSupportLoaderManager(), new AttachmentTypeListener());
      }
      attachmentTypeSelector.show(this, attachButton);
    } else {
      handleManualMmsRequired();
    }
  }

  private void handleManualMmsRequired() {
    Toast.makeText(this, R.string.MmsDownloader_error_reading_mms_settings, Toast.LENGTH_LONG).show();

    Bundle extras = getIntent().getExtras();
    Intent intent = new Intent(this, PromptMmsActivity.class);
    if (extras != null) intent.putExtras(extras);
    startActivity(intent);
  }

  private void handleUnverifiedRecipients() {
    List<Recipient>      unverifiedRecipients = identityRecords.getUnverifiedRecipients(this);
    List<IdentityRecord> unverifiedRecords    = identityRecords.getUnverifiedRecords();
    String               message              = IdentityUtil.getUnverifiedSendDialogDescription(this, unverifiedRecipients);

    if (message == null) return;

    //noinspection CodeBlock2Expr
    new UnverifiedSendDialog(this, message, unverifiedRecords, () -> {
      initializeIdentityRecords().addListener(new ListenableFuture.Listener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          sendMessage();
        }

        @Override
        public void onFailure(ExecutionException e) {
          throw new AssertionError(e);
        }
      });
    }).show();
  }

  private void handleUntrustedRecipients() {
    List<Recipient>      untrustedRecipients = identityRecords.getUntrustedRecipients(this);
    List<IdentityRecord> untrustedRecords    = identityRecords.getUntrustedRecords();
    String               untrustedMessage    = IdentityUtil.getUntrustedSendDialogDescription(this, untrustedRecipients);

    if (untrustedMessage == null) return;

    //noinspection CodeBlock2Expr
    new UntrustedSendDialog(this, untrustedMessage, untrustedRecords, () -> {
      initializeIdentityRecords().addListener(new ListenableFuture.Listener<Boolean>() {
        @Override
        public void onSuccess(Boolean result) {
          sendMessage();
        }

        @Override
        public void onFailure(ExecutionException e) {
          throw new AssertionError(e);
        }
      });
    }).show();
  }

  private void handleSecurityChange(boolean isSecureText, boolean isDefaultSms) {
    Log.i(TAG, "handleSecurityChange(" + isSecureText + ", " + isDefaultSms + ")");
    if (isSecurityInitialized && isSecureText == this.isSecureText && isDefaultSms == this.isDefaultSms) {
      return;
    }

    this.isSecureText          = isSecureText;
    this.isDefaultSms          = isDefaultSms;
    this.isSecurityInitialized = true;

    boolean isMediaMessage = recipient.get().isMmsGroup() || attachmentManager.isAttachmentPresent();

    sendButton.resetAvailableTransports(isMediaMessage);

    if (!isSecureText && !isPushGroupConversation()) sendButton.disableTransport(Type.TEXTSECURE);
    if (recipient.get().isPushGroup())            sendButton.disableTransport(Type.SMS);

    if (!recipient.get().isPushGroup() && recipient.get().isForceSmsSelection()) {
      sendButton.setDefaultTransport(Type.SMS);
    } else {
      if (isSecureText || isPushGroupConversation()) sendButton.setDefaultTransport(Type.TEXTSECURE);
      else                                           sendButton.setDefaultTransport(Type.SMS);
    }

    calculateCharactersRemaining();
    supportInvalidateOptionsMenu();
    setBlockedUserState(recipient.get(), isSecureText, isDefaultSms);
  }

  ///// Initializers

  private ListenableFuture<Boolean> initializeDraft() {
    final SettableFuture<Boolean> result = new SettableFuture<>();

    final String         draftText      = getIntent().getStringExtra(TEXT_EXTRA);
    final Uri            draftMedia     = getIntent().getData();
    final MediaType      draftMediaType = MediaType.from(getIntent().getType());
    final List<Media>    mediaList      = getIntent().getParcelableArrayListExtra(MEDIA_EXTRA);
    final StickerLocator stickerLocator = getIntent().getParcelableExtra(STICKER_EXTRA);

    if (stickerLocator != null && draftMedia != null) {
      sendSticker(stickerLocator, draftMedia, 0, true);
      return new SettableFuture<>(false);
    }

    if (!Util.isEmpty(mediaList)) {
      Intent sendIntent = MediaSendActivity.buildEditorIntent(this, mediaList, recipient.get(), draftText, sendButton.getSelectedTransport());
      startActivityForResult(sendIntent, MEDIA_SENDER);
      return new SettableFuture<>(false);
    }

    if (draftText != null) {
      composeText.setText("");
      composeText.append(draftText);
      result.set(true);
    }

    if (draftMedia != null && draftMediaType != null) {
      return setMedia(draftMedia, draftMediaType);
    }

    if (draftText == null && draftMedia == null && draftMediaType == null) {
      return initializeDraftFromDatabase();
    } else {
      updateToggleButtonState();
      result.set(false);
    }

    return result;
  }

  private void initializeEnabledCheck() {
    boolean enabled = !(isPushGroupConversation() && !isActiveGroup());
    inputPanel.setEnabled(enabled);
    sendButton.setEnabled(enabled);
    attachButton.setEnabled(enabled);
  }

  private ListenableFuture<Boolean> initializeDraftFromDatabase() {
    SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Void, Void, List<Draft>>() {
      @Override
      protected List<Draft> doInBackground(Void... params) {
        DraftDatabase draftDatabase = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        List<Draft> results         = draftDatabase.getDrafts(threadId);

        draftDatabase.clearDrafts(threadId);

        return results;
      }

      @Override
      protected void onPostExecute(List<Draft> drafts) {
        if (drafts.isEmpty()) {
          future.set(false);
          updateToggleButtonState();
          return;
        }

        AtomicInteger                      draftsRemaining = new AtomicInteger(drafts.size());
        AtomicBoolean                      success         = new AtomicBoolean(false);
        ListenableFuture.Listener<Boolean> listener        = new AssertedSuccessListener<Boolean>() {
          @Override
          public void onSuccess(Boolean result) {
            success.compareAndSet(false, result);

            if (draftsRemaining.decrementAndGet() <= 0) {
              future.set(success.get());
            }
          }
        };

        for (Draft draft : drafts) {
          try {
            switch (draft.getType()) {
              case Draft.TEXT:
                composeText.setText(draft.getValue());
                listener.onSuccess(true);
                break;
              case Draft.LOCATION:
                attachmentManager.setLocation(SignalPlace.deserialize(draft.getValue()), getCurrentMediaConstraints()).addListener(listener);
                break;
              case Draft.IMAGE:
                setMedia(Uri.parse(draft.getValue()), MediaType.IMAGE).addListener(listener);
                break;
              case Draft.AUDIO:
                setMedia(Uri.parse(draft.getValue()), MediaType.AUDIO).addListener(listener);
                break;
              case Draft.VIDEO:
                setMedia(Uri.parse(draft.getValue()), MediaType.VIDEO).addListener(listener);
                break;
              case Draft.QUOTE:
                SettableFuture<Boolean> quoteResult = new SettableFuture<>();
                new QuoteRestorationTask(draft.getValue(), quoteResult).execute();
                quoteResult.addListener(listener);
                break;
            }
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        updateToggleButtonState();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);

    return future;
  }

  private ListenableFuture<Boolean> initializeSecurity(final boolean currentSecureText,
                                                       final boolean currentIsDefaultSms)
  {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    handleSecurityChange(currentSecureText || isPushGroupConversation(), currentIsDefaultSms);

    new AsyncTask<Recipient, Void, boolean[]>() {
      @Override
      protected boolean[] doInBackground(Recipient... params) {
        Context           context         = ConversationActivity.this;
        Recipient         recipient       = params[0].resolve();
        Log.i(TAG, "Resolving registered state...");
        RegisteredState registeredState;

        if (recipient.isPushGroup()) {
          Log.i(TAG, "Push group recipient...");
          registeredState = RegisteredState.REGISTERED;
        } else {
          Log.i(TAG, "Checking through resolved recipient");
          registeredState = recipient.resolve().getRegistered();
        }

        Log.i(TAG, "Resolved registered state: " + registeredState);
        boolean           signalEnabled   = TextSecurePreferences.isPushRegistered(context);

        if (registeredState == RegisteredState.UNKNOWN) {
          try {
            Log.i(TAG, "Refreshing directory for user: " + recipient.getId().serialize());
            registeredState = DirectoryHelper.refreshDirectoryFor(context, recipient, false);
          } catch (IOException e) {
            Log.w(TAG, e);
          }
        }

        Log.i(TAG, "Returning registered state...");
        return new boolean[] {registeredState == RegisteredState.REGISTERED && signalEnabled,
                              Util.isDefaultSmsProvider(context)};
      }

      @Override
      protected void onPostExecute(boolean[] result) {
        if (result[0] != currentSecureText || result[1] != currentIsDefaultSms) {
          Log.i(TAG, "onPostExecute() handleSecurityChange: " + result[0] + " , " + result[1]);
          handleSecurityChange(result[0], result[1]);
        }
        future.set(true);
        onSecurityUpdated();
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void onSecurityUpdated() {
    Log.i(TAG, "onSecurityUpdated()");
    updateReminders();
    updateDefaultSubscriptionId(recipient.get().getDefaultSubscriptionId());
  }

  private void initializeInsightObserver() {
    inviteReminderModel = new InviteReminderModel(this, new InviteReminderRepository(this));
    inviteReminderModel.loadReminder(recipient, this::updateReminders);
  }

  protected void updateReminders() {
    Optional<Reminder> inviteReminder = inviteReminderModel.getReminder();

    if (UnauthorizedReminder.isEligible(this)) {
      reminderView.get().showReminder(new UnauthorizedReminder(this));
    } else if (ExpiredBuildReminder.isEligible()) {
      reminderView.get().showReminder(new ExpiredBuildReminder(this));
    } else if (ServiceOutageReminder.isEligible(this)) {
      ApplicationDependencies.getJobManager().add(new ServiceOutageDetectionJob());
      reminderView.get().showReminder(new ServiceOutageReminder(this));
    } else if (TextSecurePreferences.isPushRegistered(this)      &&
               TextSecurePreferences.isShowInviteReminders(this) &&
               !isSecureText                                     &&
               inviteReminder.isPresent()                        &&
               !recipient.get().isGroup()) {
      reminderView.get().setOnActionClickListener(this::handleReminderAction);
      reminderView.get().setOnDismissListener(() -> inviteReminderModel.dismissReminder());
      reminderView.get().showReminder(inviteReminder.get());
    } else if (reminderView.resolved()) {
      reminderView.get().hide();
    }
  }

  private void handleReminderAction(@IdRes int reminderActionId) {
    switch (reminderActionId) {
      case R.id.reminder_action_invite:
        handleInviteLink();
        reminderView.get().requestDismiss();
        break;
      case R.id.reminder_action_view_insights:
        InsightsLauncher.showInsightsDashboard(getSupportFragmentManager());
        break;
      default:
        throw new IllegalArgumentException("Unknown ID: " + reminderActionId);
    }
  }

  private void updateDefaultSubscriptionId(Optional<Integer> defaultSubscriptionId) {
    Log.i(TAG, "updateDefaultSubscriptionId(" + defaultSubscriptionId.orNull() + ")");
    sendButton.setDefaultSubscriptionId(defaultSubscriptionId);
  }

  private void initializeMmsEnabledCheck() {
    new AsyncTask<Void, Void, Boolean>() {
      @Override
      protected Boolean doInBackground(Void... params) {
        return Util.isMmsCapable(ConversationActivity.this);
      }

      @Override
      protected void onPostExecute(Boolean isMmsEnabled) {
        ConversationActivity.this.isMmsEnabled = isMmsEnabled;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  private ListenableFuture<Boolean> initializeIdentityRecords() {
    final SettableFuture<Boolean> future = new SettableFuture<>();

    new AsyncTask<Recipient, Void, Pair<IdentityRecordList, String>>() {
      @Override
      protected @NonNull Pair<IdentityRecordList, String> doInBackground(Recipient... params) {
        IdentityDatabase   identityDatabase   = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);
        IdentityRecordList identityRecordList = new IdentityRecordList();
        List<Recipient>    recipients         = new LinkedList<>();

        if (params[0].isGroup()) {
          recipients.addAll(DatabaseFactory.getGroupDatabase(ConversationActivity.this)
                                           .getGroupMembers(params[0].requireGroupId(), false));
        } else {
          recipients.add(params[0]);
        }

        for (Recipient recipient : recipients) {
          Log.i(TAG, "Loading identity for: " + recipient.getId());
          identityRecordList.add(identityDatabase.getIdentity(recipient.getId()));
        }

        String message = null;

        if (identityRecordList.isUnverified()) {
          message = IdentityUtil.getUnverifiedBannerDescription(ConversationActivity.this, identityRecordList.getUnverifiedRecipients(ConversationActivity.this));
        }

        return new Pair<>(identityRecordList, message);
      }

      @Override
      protected void onPostExecute(@NonNull Pair<IdentityRecordList, String> result) {
        Log.i(TAG, "Got identity records: " + result.first.isUnverified());
        identityRecords.replaceWith(result.first);

        if (result.second != null) {
          Log.d(TAG, "Replacing banner...");
          unverifiedBannerView.get().display(result.second, result.first.getUnverifiedRecords(),
                                             new UnverifiedClickedListener(),
                                             new UnverifiedDismissedListener());
        } else if (unverifiedBannerView.resolved()) {
          Log.d(TAG, "Clearing banner...");
          unverifiedBannerView.get().hide();
        }

        titleView.setVerified(isSecureText && identityRecords.isVerified());

        future.set(true);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, recipient.get());

    return future;
  }

  private void initializeViews() {
    titleView              = findViewById(R.id.conversation_title_view);
    buttonToggle           = ViewUtil.findById(this, R.id.button_toggle);
    sendButton             = ViewUtil.findById(this, R.id.send_button);
    attachButton           = ViewUtil.findById(this, R.id.attach_button);
    composeText            = ViewUtil.findById(this, R.id.embedded_text_editor);
    charactersLeft         = ViewUtil.findById(this, R.id.space_left);
    emojiDrawerStub        = ViewUtil.findStubById(this, R.id.emoji_drawer_stub);
    unblockButton          = ViewUtil.findById(this, R.id.unblock_button);
    makeDefaultSmsButton   = ViewUtil.findById(this, R.id.make_default_sms_button);
    registerButton         = ViewUtil.findById(this, R.id.register_button);
    composePanel           = ViewUtil.findById(this, R.id.bottom_panel);
    container              = ViewUtil.findById(this, R.id.layout_container);
    reminderView           = ViewUtil.findStubById(this, R.id.reminder_stub);
    unverifiedBannerView   = ViewUtil.findStubById(this, R.id.unverified_banner_stub);
    groupShareProfileView  = ViewUtil.findStubById(this, R.id.group_share_profile_view_stub);
    quickAttachmentToggle  = ViewUtil.findById(this, R.id.quick_attachment_toggle);
    inlineAttachmentToggle = ViewUtil.findById(this, R.id.inline_attachment_container);
    inputPanel             = ViewUtil.findById(this, R.id.bottom_panel);
    searchNav              = ViewUtil.findById(this, R.id.conversation_search_nav);
    messageRequestOverlay  = ViewUtil.findById(this, R.id.fragment_overlay_container);
    reactionOverlay        = ViewUtil.findById(this, R.id.conversation_reaction_scrubber);

    ImageButton quickCameraToggle      = ViewUtil.findById(this, R.id.quick_camera_toggle);
    ImageButton inlineAttachmentButton = ViewUtil.findById(this, R.id.inline_attachment_button);

    container.addOnKeyboardShownListener(this);
    inputPanel.setListener(this);
    inputPanel.setMediaListener(this);

    attachmentTypeSelector = null;
    attachmentManager      = new AttachmentManager(this, this);
    audioRecorder          = new AudioRecorder(this);
    typingTextWatcher      = new TypingStatusTextWatcher();

    SendButtonListener        sendButtonListener        = new SendButtonListener();
    ComposeKeyPressedListener composeKeyPressedListener = new ComposeKeyPressedListener();

    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setCursorPositionChangedListener(this);
    attachButton.setOnClickListener(new AttachButtonListener());
    attachButton.setOnLongClickListener(new AttachButtonLongClickListener());
    sendButton.setOnClickListener(sendButtonListener);
    sendButton.setEnabled(true);
    sendButton.addOnTransportChangedListener((newTransport, manuallySelected) -> {
      calculateCharactersRemaining();
      updateLinkPreviewState();
      composeText.setTransport(newTransport);

      buttonToggle.getBackground().setColorFilter(newTransport.getBackgroundColor(), PorterDuff.Mode.MULTIPLY);
      buttonToggle.getBackground().invalidateSelf();

      if (manuallySelected) recordTransportPreference(newTransport);
    });

    titleView.setOnClickListener(v -> handleConversationSettings());
    titleView.setOnLongClickListener(v -> handleDisplayQuickContact());
    unblockButton.setOnClickListener(v -> handleUnblock());
    makeDefaultSmsButton.setOnClickListener(v -> handleMakeDefaultSms());
    registerButton.setOnClickListener(v -> handleRegisterForSignal());

    composeText.setOnKeyListener(composeKeyPressedListener);
    composeText.addTextChangedListener(composeKeyPressedListener);
    composeText.setOnEditorActionListener(sendButtonListener);
    composeText.setOnClickListener(composeKeyPressedListener);
    composeText.setOnFocusChangeListener(composeKeyPressedListener);

    if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA) && Camera.getNumberOfCameras() > 0) {
      quickCameraToggle.setVisibility(View.VISIBLE);
      quickCameraToggle.setOnClickListener(new QuickCameraToggleListener());
    } else {
      quickCameraToggle.setVisibility(View.GONE);
    }

    searchNav.setEventListener(this);

    inlineAttachmentButton.setOnClickListener(v -> handleAddAttachment());

    reactionOverlay.setOnReactionSelectedListener(this::onReactionSelected);
  }

  protected void initializeActionBar() {
    Toolbar toolbar = findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();

    supportActionBar.setDisplayHomeAsUpEnabled(true);
    supportActionBar.setDisplayShowTitleEnabled(false);
  }

  private void initializeResources() {
    if (recipient != null) {
      recipient.removeObservers(this);
    }

    recipient        = Recipient.live(getIntent().getParcelableExtra(RECIPIENT_EXTRA));
    threadId         = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    archived         = getIntent().getBooleanExtra(IS_ARCHIVED_EXTRA, false);
    distributionType = getIntent().getIntExtra(DISTRIBUTION_TYPE_EXTRA, ThreadDatabase.DistributionTypes.DEFAULT);
    glideRequests    = GlideApp.with(this);

    recipient.observe(this, this::onRecipientChanged);
  }


  private void initializeLinkPreviewObserver() {
    linkPreviewViewModel = ViewModelProviders.of(this, new LinkPreviewViewModel.Factory(new LinkPreviewRepository())).get(LinkPreviewViewModel.class);

    if (!TextSecurePreferences.isLinkPreviewsEnabled(this)) {
      linkPreviewViewModel.onUserCancel();
      return;
    }

    linkPreviewViewModel.getLinkPreviewState().observe(this, previewState -> {
      if (previewState == null) return;

      if (previewState.isLoading()) {
        Log.d(TAG, "Loading link preview.");
        inputPanel.setLinkPreviewLoading();
      } else {
        Log.d(TAG, "Setting link preview: " + previewState.getLinkPreview().isPresent());
        inputPanel.setLinkPreview(glideRequests, previewState.getLinkPreview());
      }

      updateToggleButtonState();
    });
  }

  private void initializeSearchObserver() {
    searchViewModel = ViewModelProviders.of(this).get(ConversationSearchViewModel.class);

    searchViewModel.getSearchResults().observe(this, result -> {
      if (result == null) return;

      if (!result.getResults().isEmpty()) {
        MessageResult messageResult = result.getResults().get(result.getPosition());
        fragment.jumpToMessage(messageResult.messageRecipient.getId(), messageResult.receivedTimestampMs, searchViewModel::onMissingResult);
      }

      searchNav.setData(result.getPosition(), result.getResults().size());
    });
  }

  private void initializeStickerObserver() {
    StickerSearchRepository repository = new StickerSearchRepository(this);

    stickerViewModel = ViewModelProviders.of(this, new ConversationStickerViewModel.Factory(getApplication(), repository))
                                         .get(ConversationStickerViewModel.class);

    stickerViewModel.getStickerResults().observe(this, stickers -> {
      if (stickers == null) return;
      if(BuildConfig.SIGNAL_CDN.isEmpty()) {
        return;
      }
      inputPanel.setStickerSuggestions(stickers);
    });

    stickerViewModel.getStickersAvailability().observe(this, stickersAvailable -> {
      if (stickersAvailable == null) return;


      boolean           isSystemEmojiPreferred = TextSecurePreferences.isSystemEmojiPreferred(this);
      MediaKeyboardMode keyboardMode           = TextSecurePreferences.getMediaKeyboardMode(this);
      boolean           stickerIntro           = !TextSecurePreferences.hasSeenStickerIntroTooltip(this);

      if (stickersAvailable) {
        inputPanel.showMediaKeyboardToggle(true);
        if(BuildConfig.SIGNAL_CDN.isEmpty()) {
          inputPanel.setMediaKeyboardToggleMode(isSystemEmojiPreferred);
        } else {
          inputPanel.setMediaKeyboardToggleMode(isSystemEmojiPreferred || keyboardMode == MediaKeyboardMode.STICKER);
          if (stickerIntro) showStickerIntroductionTooltip();
        }
      }

      if (emojiDrawerStub.resolved()) {
        initializeMediaKeyboardProviders(emojiDrawerStub.get(), stickersAvailable);
      }
    });
  }

  private void showStickerIntroductionTooltip() {
    if(BuildConfig.SIGNAL_CDN.isEmpty()) {
      return;
    }
    TextSecurePreferences.setMediaKeyboardMode(this, MediaKeyboardMode.STICKER);
    inputPanel.setMediaKeyboardToggleMode(true);

    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setBackgroundTint(getResources().getColor(R.color.core_blue))
                .setTextColor(getResources().getColor(R.color.core_white))
                .setText(R.string.ConversationActivity_new_say_it_with_stickers)
                .setOnDismissListener(() -> {
                  TextSecurePreferences.setHasSeenStickerIntroTooltip(this, true);
                  EventBus.getDefault().removeStickyEvent(StickerPackInstallEvent.class);
                })
                .show(TooltipPopup.POSITION_ABOVE);
  }


  private void onReactionSelected(MessageRecord messageRecord, String emoji) {
    final Context context = getApplicationContext();

    SignalExecutors.BOUNDED.execute(() -> {
      ReactionRecord oldRecord = Stream.of(messageRecord.getReactions())
                                       .filter(record -> record.getAuthor().equals(Recipient.self().getId()))
                                       .findFirst()
                                       .orElse(null);

      if (oldRecord != null && oldRecord.getEmoji().equals(emoji)) {
        MessageSender.sendReactionRemoval(context, messageRecord.getId(), messageRecord.isMms(), oldRecord);
      } else {
        MessageSender.sendNewReaction(context, messageRecord.getId(), messageRecord.isMms(), emoji);
      }
    });
  }

  @Override
  public void onSearchMoveUpPressed() {
    searchViewModel.onMoveUp();
  }

  @Override
  public void onSearchMoveDownPressed() {
    searchViewModel.onMoveDown();
  }

  private void initializeProfiles() {
    if (!isSecureText) {
      Log.i(TAG, "SMS contact, no profile fetch needed.");
      return;
    }

    ApplicationDependencies.getJobManager().add(new RetrieveProfileJob(recipient.get()));
  }

  private void onRecipientChanged(@NonNull Recipient recipient) {
    Log.i(TAG, "onModified(" + recipient.getId() + ") " + recipient.getRegistered());
    titleView.setTitle(glideRequests, recipient);
    titleView.setVerified(identityRecords.isVerified());
    setBlockedUserState(recipient, isSecureText, isDefaultSms);
    setActionBarColor(recipient.getColor());
    setGroupShareProfileReminder(recipient);
    updateReminders();
    updateDefaultSubscriptionId(recipient.getDefaultSubscriptionId());
    initializeSecurity(isSecureText, isDefaultSms);

    if (searchViewItem == null || !searchViewItem.isActionViewExpanded()) {
      invalidateOptionsMenu();
    }
  }

  @Subscribe(threadMode = ThreadMode.MAIN)
  public void onIdentityRecordUpdate(final IdentityRecord event) {
    initializeIdentityRecords();
  }

  @Subscribe(threadMode =  ThreadMode.MAIN, sticky = true)
  public void onStickerPackInstalled(final StickerPackInstallEvent event) {
    if (!TextSecurePreferences.hasSeenStickerIntroTooltip(this)) return;

    EventBus.getDefault().removeStickyEvent(event);
    TooltipPopup.forTarget(inputPanel.getMediaKeyboardToggleAnchorView())
                .setText(R.string.ConversationActivity_sticker_pack_installed)
                .setIconGlideModel(event.getIconGlideModel())
                .show(TooltipPopup.POSITION_ABOVE);
  }

  private void initializeReceivers() {
    securityUpdateReceiver = new BroadcastReceiver() {
      @Override
      public void onReceive(Context context, Intent intent) {
        initializeSecurity(isSecureText, isDefaultSms);
        calculateCharactersRemaining();
      }
    };

    registerReceiver(securityUpdateReceiver,
                     new IntentFilter(SecurityEvent.SECURITY_UPDATE_EVENT),
                     KeyCachingService.KEY_PERMISSION, null);
  }

  //////// Helper Methods

  private void addAttachment(int type) {
    linkPreviewViewModel.onUserCancel();

    Log.i(TAG, "Selected: " + type);
    switch (type) {
    case AttachmentTypeSelector.ADD_GALLERY:
      AttachmentManager.selectGallery(this, MEDIA_SENDER, recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()); break;
    case AttachmentTypeSelector.ADD_DOCUMENT:
      AttachmentManager.selectDocument(this, PICK_DOCUMENT); break;
    case AttachmentTypeSelector.ADD_SOUND:
      AttachmentManager.selectAudio(this, PICK_AUDIO); break;
    case AttachmentTypeSelector.ADD_CONTACT_INFO:
      AttachmentManager.selectContactInfo(this, PICK_CONTACT); break;
    case AttachmentTypeSelector.ADD_LOCATION:
      AttachmentManager.selectLocation(this, PICK_LOCATION); break;
    case AttachmentTypeSelector.TAKE_PHOTO:
      attachmentManager.capturePhoto(this, TAKE_PHOTO); break;
    case AttachmentTypeSelector.ADD_GIF:
      AttachmentManager.selectGif(this, PICK_GIF, !isSecureText, recipient.get().getColor().toConversationColor(this)); break;
    }
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType) {
    return setMedia(uri, mediaType, 0, 0);
  }

  private ListenableFuture<Boolean> setMedia(@Nullable Uri uri, @NonNull MediaType mediaType, int width, int height) {
    if (uri == null) {
      return new SettableFuture<>(false);
    }

    if (MediaType.VCARD.equals(mediaType) && isSecureText) {
      openContactShareEditor(uri);
      return new SettableFuture<>(false);
    } else if (MediaType.IMAGE.equals(mediaType) || MediaType.GIF.equals(mediaType) || MediaType.VIDEO.equals(mediaType)) {
      Media media = new Media(uri, MediaUtil.getMimeType(this, uri), 0, width, height, 0, Optional.absent(), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
      return new SettableFuture<>(false);
    } else {
      return attachmentManager.setMedia(glideRequests, uri, mediaType, getCurrentMediaConstraints(), width, height);
    }
  }

  private void openContactShareEditor(Uri contactUri) {
    Intent intent = ContactShareEditActivity.getIntent(this, Collections.singletonList(contactUri));
    startActivityForResult(intent, GET_CONTACT_DETAILS);
  }

  private void addAttachmentContactInfo(Uri contactUri) {
    ContactAccessor contactDataList = ContactAccessor.getInstance();
    ContactData contactData = contactDataList.getContactData(this, contactUri);

    if      (contactData.numbers.size() == 1) composeText.append(contactData.numbers.get(0).number);
    else if (contactData.numbers.size() > 1)  selectContactInfo(contactData);
  }

  private void sendSharedContact(List<Contact> contacts) {
    int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
    boolean    initiating     = threadId == -1;

    sendMediaMessage(isSmsForced(), "", attachmentManager.buildSlideDeck(), null, contacts, Collections.emptyList(), expiresIn, false, subscriptionId, initiating, false);
  }

  private void selectContactInfo(ContactData contactData) {
    final CharSequence[] numbers     = new CharSequence[contactData.numbers.size()];
    final CharSequence[] numberItems = new CharSequence[contactData.numbers.size()];

    for (int i = 0; i < contactData.numbers.size(); i++) {
      numbers[i]     = contactData.numbers.get(i).number;
      numberItems[i] = contactData.numbers.get(i).type + ": " + contactData.numbers.get(i).number;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setIconAttribute(R.attr.conversation_attach_contact_info);
    builder.setTitle(R.string.ConversationActivity_select_contact_info);

    builder.setItems(numberItems, (dialog, which) -> composeText.append(numbers[which]));
    builder.show();
  }

  private Drafts getDraftsForCurrentState() {
    Drafts drafts = new Drafts();

    if (!Util.isEmpty(composeText)) {
      drafts.add(new Draft(Draft.TEXT, composeText.getTextTrimmed()));
    }

    for (Slide slide : attachmentManager.buildSlideDeck().getSlides()) {
      if      (slide.hasAudio() && slide.getUri() != null)    drafts.add(new Draft(Draft.AUDIO, slide.getUri().toString()));
      else if (slide.hasVideo() && slide.getUri() != null)    drafts.add(new Draft(Draft.VIDEO, slide.getUri().toString()));
      else if (slide.hasLocation())                           drafts.add(new Draft(Draft.LOCATION, ((LocationSlide)slide).getPlace().serialize()));
      else if (slide.hasImage() && slide.getUri() != null)    drafts.add(new Draft(Draft.IMAGE, slide.getUri().toString()));
    }

    Optional<QuoteModel> quote = inputPanel.getQuote();

    if (quote.isPresent()) {
      drafts.add(new Draft(Draft.QUOTE, new QuoteId(quote.get().getId(), quote.get().getAuthor()).serialize()));
    }

    return drafts;
  }

  protected ListenableFuture<Long> saveDraft() {
    final SettableFuture<Long> future = new SettableFuture<>();

    if (this.recipient == null) {
      future.set(threadId);
      return future;
    }

    final Drafts       drafts               = getDraftsForCurrentState();
    final long         thisThreadId         = this.threadId;
    final int          thisDistributionType = this.distributionType;

    new AsyncTask<Long, Void, Long>() {
      @Override
      protected Long doInBackground(Long... params) {
        ThreadDatabase threadDatabase = DatabaseFactory.getThreadDatabase(ConversationActivity.this);
        DraftDatabase  draftDatabase  = DatabaseFactory.getDraftDatabase(ConversationActivity.this);
        long           threadId       = params[0];

        if (drafts.size() > 0) {
          if (threadId == -1) threadId = threadDatabase.getThreadIdFor(getRecipient(), thisDistributionType);

          draftDatabase.insertDrafts(threadId, drafts);
          threadDatabase.updateSnippet(threadId, drafts.getSnippet(ConversationActivity.this),
                                       drafts.getUriSnippet(),
                                       System.currentTimeMillis(), Types.BASE_DRAFT_TYPE, true);
        } else if (threadId > 0) {
          threadDatabase.update(threadId, false);
        }

        return threadId;
      }

      @Override
      protected void onPostExecute(Long result) {
        future.set(result);
      }

    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, thisThreadId);

    return future;
  }

  private void setActionBarColor(MaterialColor color) {
    ActionBar supportActionBar = getSupportActionBar();
    if (supportActionBar == null) throw new AssertionError();
    supportActionBar.setBackgroundDrawable(new ColorDrawable(color.toActionBarColor(this)));
    setStatusBarColor(color.toStatusBarColor(this));
  }

  private void setBlockedUserState(Recipient recipient, boolean isSecureText, boolean isDefaultSms) {
    if (recipient.isBlocked()) {
      unblockButton.setVisibility(View.VISIBLE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    } else if (!isSecureText && isPushGroupConversation()) {
      unblockButton.setVisibility(View.GONE);
      composePanel.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.VISIBLE);
    } else if (!isSecureText && !isDefaultSms) {
      unblockButton.setVisibility(View.GONE);
      composePanel.setVisibility(View.GONE);
      //makeDefaultSmsButton.setVisibility(View.VISIBLE);
      registerButton.setVisibility(View.GONE);
    } else {
      composePanel.setVisibility(View.VISIBLE);
      unblockButton.setVisibility(View.GONE);
      makeDefaultSmsButton.setVisibility(View.GONE);
      registerButton.setVisibility(View.GONE);
    }
  }

  private void setGroupShareProfileReminder(@NonNull Recipient recipient) {
    if (!FeatureFlags.MESSAGE_REQUESTS && recipient.isPushGroup() && !recipient.isProfileSharing()) {
      groupShareProfileView.get().setRecipient(recipient);
      groupShareProfileView.get().setVisibility(View.VISIBLE);
    } else if (groupShareProfileView.resolved()) {
      groupShareProfileView.get().setVisibility(View.GONE);
    }
  }

  private void calculateCharactersRemaining() {
    String          messageBody     = composeText.getTextTrimmed();
    TransportOption transportOption = sendButton.getSelectedTransport();
    CharacterState  characterState  = transportOption.calculateCharacters(messageBody);

    if (characterState.charactersRemaining <= 15 || characterState.messagesSpent > 1) {
      charactersLeft.setText(String.format(dynamicLanguage.getCurrentLocale(),
                                           "%d/%d (%d)",
                                           characterState.charactersRemaining,
                                           characterState.maxTotalMessageSize,
                                           characterState.messagesSpent));
      charactersLeft.setVisibility(View.VISIBLE);
    } else {
      charactersLeft.setVisibility(View.GONE);
    }
  }

  private void initializeMediaKeyboardProviders(@NonNull MediaKeyboard mediaKeyboard, boolean stickersAvailable) {
    boolean isSystemEmojiPreferred   = TextSecurePreferences.isSystemEmojiPreferred(this);
    boolean isStickersAvailable = stickersAvailable;
    if(BuildConfig.SIGNAL_CDN.isEmpty()) {
      isStickersAvailable = false;
    }

    if (isStickersAvailable) {
      if (isSystemEmojiPreferred) {
        mediaKeyboard.setProviders(0, new StickerKeyboardProvider(this, this));
      } else {
        MediaKeyboardMode keyboardMode = TextSecurePreferences.getMediaKeyboardMode(this);
        int               index        = keyboardMode == MediaKeyboardMode.STICKER ? 1 : 0;

        mediaKeyboard.setProviders(index,
                                   new EmojiKeyboardProvider(this, inputPanel),
                                   new StickerKeyboardProvider(this, this));
      }
    } else if (!isSystemEmojiPreferred) {
      mediaKeyboard.setProviders(0, new EmojiKeyboardProvider(this, inputPanel));
    }
  }


  private boolean isSingleConversation() {
    return getRecipient() != null && !getRecipient().isGroup();
  }

  private boolean isActiveGroup() {
    if (!isGroupConversation()) return false;

    Optional<GroupRecord> record = DatabaseFactory.getGroupDatabase(this).getGroup(getRecipient().getId());
    return record.isPresent() && record.get().isActive();
  }

  @SuppressWarnings("SimplifiableIfStatement")
  private boolean isSelfConversation() {
    if (!TextSecurePreferences.isPushRegistered(this)) return false;
    if (recipient.get().isGroup())                     return false;

    return recipient.get().isLocalNumber();
  }

  private boolean isGroupConversation() {
    return getRecipient() != null && getRecipient().isGroup();
  }

  private boolean isPushGroupConversation() {
    return getRecipient() != null && getRecipient().isPushGroup();
  }

  private boolean isSmsForced() {
    return sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
  }

  protected Recipient getRecipient() {
    return this.recipient.get();
  }

  protected long getThreadId() {
    return this.threadId;
  }

  private String getMessage() throws InvalidMessageException {
    String rawText = composeText.getTextTrimmed();

    if (rawText.length() < 1 && !attachmentManager.isAttachmentPresent())
      throw new InvalidMessageException(getString(R.string.ConversationActivity_message_is_empty_exclamation));

    return rawText;
  }

  private Pair<String, Optional<Slide>> getSplitMessage(String rawText, int maxPrimaryMessageSize) {
    String          bodyText  = rawText;
    Optional<Slide> textSlide = Optional.absent();

    if (bodyText.length() > maxPrimaryMessageSize) {
      bodyText = rawText.substring(0, maxPrimaryMessageSize);

      byte[] textData  = rawText.getBytes();
      String timestamp = new SimpleDateFormat("yyyy-MM-dd-HHmmss", Locale.US).format(new Date());
      String filename  = String.format(BuildConfig.SIGNAL_FILENAME_PREFIX + "-%s.txt", timestamp);
      Uri    textUri   = BlobProvider.getInstance()
                                     .forData(textData)
                                     .withMimeType(MediaUtil.LONG_TEXT)
                                     .withFileName(filename)
                                     .createForSingleSessionInMemory();

      textSlide = Optional.of(new TextSlide(this, textUri, filename, textData.length));
    }

    return new Pair<>(bodyText, textSlide);
  }

  private MediaConstraints getCurrentMediaConstraints() {
    return sendButton.getSelectedTransport().getType() == Type.TEXTSECURE
           ? MediaConstraints.getPushMediaConstraints()
           : MediaConstraints.getMmsMediaConstraints(sendButton.getSelectedTransport().getSimSubscriptionId().or(-1));
  }

  private void markThreadAsRead() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        Context                 context    = ConversationActivity.this;
        List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(params[0], false);

        MessageNotifier.updateNotification(context);
        MarkReadReceiver.process(context, messageIds);

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  private void markLastSeen() {
    new AsyncTask<Long, Void, Void>() {
      @Override
      protected Void doInBackground(Long... params) {
        DatabaseFactory.getThreadDatabase(ConversationActivity.this).setLastSeen(params[0]);
        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, threadId);
  }

  protected void sendComplete(long threadId) {
    boolean refreshFragment = (threadId != this.threadId);
    this.threadId = threadId;

    if (fragment == null || !fragment.isVisible() || isFinishing()) {
      return;
    }

    fragment.setLastSeen(0);

    if (refreshFragment) {
      fragment.reload(recipient.get(), threadId);
      MessageNotifier.setVisibleThread(threadId);
    }

    fragment.scrollToBottom();
    attachmentManager.cleanup();

    updateLinkPreviewState();
  }

  private void sendMessage() {
    if (inputPanel.isRecordingInLockedMode()) {
      inputPanel.releaseRecordingLock();
      return;
    }

    try {
      Recipient recipient = getRecipient();

      if (recipient == null) {
        throw new RecipientFormattingException("Badly formatted");
      }

      String          message        = getMessage();
      TransportOption transport      = sendButton.getSelectedTransport();
      boolean         forceSms       = (recipient.isForceSmsSelection() || sendButton.isManualSelection()) && transport.isSms();
      int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
      long            expiresIn      = recipient.getExpireMessages() * 1000L;
      boolean         initiating     = threadId == -1;
      boolean         needsSplit     = !transport.isSms() && message.length() > transport.calculateCharacters(message).maxPrimaryMessageSize;
      boolean         isMediaMessage = attachmentManager.isAttachmentPresent() ||
                                       recipient.isGroup()                     ||
                                       recipient.getEmail().isPresent()        ||
                                       inputPanel.getQuote().isPresent()       ||
                                       linkPreviewViewModel.hasLinkPreview()   ||
                                       needsSplit;

      Log.i(TAG, "isManual Selection: " + sendButton.isManualSelection());
      Log.i(TAG, "forceSms: " + forceSms);

      if ((recipient.isMmsGroup() || recipient.getEmail().isPresent()) && !isMmsEnabled) {
        handleManualMmsRequired();
      } else if (!forceSms && identityRecords.isUnverified()) {
        handleUnverifiedRecipients();
      } else if (!forceSms && identityRecords.isUntrusted()) {
        handleUntrustedRecipients();
      } else if (isMediaMessage) {
        sendMediaMessage(forceSms, expiresIn, false, subscriptionId, initiating);
      } else {
        sendTextMessage(forceSms, expiresIn, subscriptionId, initiating);
      }
    } catch (RecipientFormattingException ex) {
      Toast.makeText(ConversationActivity.this,
                     R.string.ConversationActivity_recipient_is_not_a_valid_sms_or_email_address_exclamation,
                     Toast.LENGTH_LONG).show();
      Log.w(TAG, ex);
    } catch (InvalidMessageException ex) {
      Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_message_is_empty_exclamation,
                     Toast.LENGTH_SHORT).show();
      Log.w(TAG, ex);
    }
  }

  private void sendMediaMessage(final boolean forceSms, final long expiresIn, final boolean viewOnce, final int subscriptionId, boolean initiating)
      throws InvalidMessageException
  {
    Log.i(TAG, "Sending media message...");
    sendMediaMessage(forceSms, getMessage(), attachmentManager.buildSlideDeck(), inputPanel.getQuote().orNull(), Collections.emptyList(), linkPreviewViewModel.getActiveLinkPreviews(), expiresIn, viewOnce, subscriptionId, initiating, true);
  }

  private ListenableFuture<Void> sendMediaMessage(final boolean forceSms,
                                                  String body,
                                                  SlideDeck slideDeck,
                                                  QuoteModel quote,
                                                  List<Contact> contacts,
                                                  List<LinkPreview> previews,
                                                  final long expiresIn,
                                                  final boolean viewOnce,
                                                  final int subscriptionId,
                                                  final boolean initiating,
                                                  final boolean clearComposeBox)
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return new SettableFuture<>(null);
    }

    if (isSecureText && !forceSms) {
      Pair<String, Optional<Slide>> splitMessage = getSplitMessage(body, sendButton.getSelectedTransport().calculateCharacters(body).maxPrimaryMessageSize);
      body = splitMessage.first;

      if (splitMessage.second.isPresent()) {
        slideDeck.addSlide(splitMessage.second.get());
      }
    }

    OutgoingMediaMessage outgoingMessageCandidate = new OutgoingMediaMessage(recipient.get(), slideDeck, body, System.currentTimeMillis(), subscriptionId, expiresIn, viewOnce, distributionType, quote, contacts, previews);

    final SettableFuture<Void> future  = new SettableFuture<>();
    final Context              context = getApplicationContext();

    final OutgoingMediaMessage outgoingMessage;

    if (isSecureText && !forceSms) {
      outgoingMessage = new OutgoingSecureMediaMessage(outgoingMessageCandidate);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      outgoingMessage = outgoingMessageCandidate;
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS, Manifest.permission.READ_SMS)
               .ifNecessary(!isSecureText || forceSms)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 if (clearComposeBox) {
                   inputPanel.clearQuote();
                   attachmentManager.clear(glideRequests, false);
                   silentlySetComposeText("");
                 }

                 final long id = fragment.stageOutgoingMessage(outgoingMessage);

                 new AsyncTask<Void, Void, Long>() {
                   @Override
                   protected Long doInBackground(Void... param) {
                     if (initiating) {
                       DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient.getId(), true);
                     }

                     return MessageSender.send(context, outgoingMessage, threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
                   }

                   @Override
                   protected void onPostExecute(Long result) {
                     sendComplete(result);
                     future.set(null);
                   }
                 }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
               })
               .onAnyDenied(() -> future.set(null))
               .execute();

    return future;
  }

  private void sendTextMessage(final boolean forceSms, final long expiresIn, final int subscriptionId, final boolean initiatingConversation)
      throws InvalidMessageException
  {
    if (!isDefaultSms && (!isSecureText || forceSms)) {
      showDefaultSmsPrompt();
      return;
    }

    final Context context     = getApplicationContext();
    final String  messageBody = getMessage();

    OutgoingTextMessage message;

    if (isSecureText && !forceSms) {
      message = new OutgoingEncryptedMessage(recipient.get(), messageBody, expiresIn);
      ApplicationContext.getInstance(context).getTypingStatusSender().onTypingStopped(threadId);
    } else {
      message = new OutgoingTextMessage(recipient.get(), messageBody, expiresIn, subscriptionId);
    }

    Permissions.with(this)
               .request(Manifest.permission.SEND_SMS)
               .ifNecessary(forceSms || !isSecureText)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_sms_permission_in_order_to_send_an_sms))
               .onAllGranted(() -> {
                 silentlySetComposeText("");
                 final long id = fragment.stageOutgoingMessage(message);

                 new AsyncTask<OutgoingTextMessage, Void, Long>() {
                   @Override
                   protected Long doInBackground(OutgoingTextMessage... messages) {
                     if (initiatingConversation) {
                       DatabaseFactory.getRecipientDatabase(context).setProfileSharing(recipient.getId(), true);
                     }

                     return MessageSender.send(context, messages[0], threadId, forceSms, () -> fragment.releaseOutgoingMessage(id));
                   }

                   @Override
                   protected void onPostExecute(Long result) {
                     sendComplete(result);
                   }
                 }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, message);

               })
               .execute();
  }

  private void showDefaultSmsPrompt() {
    new AlertDialog.Builder(this)
                   .setMessage(R.string.ConversationActivity_signal_cannot_sent_sms_mms_messages_because_it_is_not_your_default_sms_app)
                   .setNegativeButton(R.string.ConversationActivity_no, (dialog, which) -> dialog.dismiss())
                   .setPositiveButton(R.string.ConversationActivity_yes, (dialog, which) -> handleMakeDefaultSms())
                   .show();
  }

  private void updateToggleButtonState() {
    if (inputPanel.isRecordingInLockedMode()) {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.show();
      inlineAttachmentToggle.hide();
      return;
    }

    if (composeText.getText().length() == 0 && !attachmentManager.isAttachmentPresent()) {
      buttonToggle.display(attachButton);
      quickAttachmentToggle.show();
      inlineAttachmentToggle.hide();
    } else {
      buttonToggle.display(sendButton);
      quickAttachmentToggle.hide();

      if (!attachmentManager.isAttachmentPresent() && !linkPreviewViewModel.hasLinkPreview()) {
        inlineAttachmentToggle.show();
      } else {
        inlineAttachmentToggle.hide();
      }
    }
  }

  private void updateLinkPreviewState() {
    if (TextSecurePreferences.isLinkPreviewsEnabled(this) && !sendButton.getSelectedTransport().isSms() && !attachmentManager.isAttachmentPresent()) {
      linkPreviewViewModel.onEnabled();
      linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), composeText.getSelectionStart(), composeText.getSelectionEnd());
    } else {
      linkPreviewViewModel.onUserCancel();
    }
  }

  private void recordTransportPreference(TransportOption transportOption) {
    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... params) {
        RecipientDatabase recipientDatabase = DatabaseFactory.getRecipientDatabase(ConversationActivity.this);

        recipientDatabase.setDefaultSubscriptionId(recipient.getId(), transportOption.getSimSubscriptionId().or(-1));

        if (!recipient.resolve().isPushGroup()) {
          recipientDatabase.setForceSmsSelection(recipient.getId(), recipient.get().getRegistered() == RegisteredState.REGISTERED && transportOption.isSms());
        }

        return null;
      }
    }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
  }

  @Override
  public void onRecorderPermissionRequired() {
    Permissions.with(this)
               .request(Manifest.permission.RECORD_AUDIO)
               .ifNecessary()
               .withRationaleDialog(getString(R.string.ConversationActivity_to_send_audio_messages_allow_signal_access_to_your_microphone), R.drawable.ic_mic_solid_24)
               .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_requires_the_microphone_permission_in_order_to_send_audio_messages))
               .execute();
  }

  @Override
  public void onRecorderStarted() {
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    audioRecorder.startRecording();
  }

  @Override
  public void onRecorderLocked() {
    updateToggleButtonState();
  }

  @Override
  public void onRecorderFinished() {
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(20);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final @NonNull Pair<Uri, Long> result) {
        boolean    forceSms       = sendButton.isManualSelection() && sendButton.getSelectedTransport().isSms();
        int        subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
        long       expiresIn      = recipient.get().getExpireMessages() * 1000L;
        boolean    initiating     = threadId == -1;
        AudioSlide audioSlide     = new AudioSlide(ConversationActivity.this, result.first, result.second, MediaUtil.AUDIO_AAC, true);
        SlideDeck  slideDeck      = new SlideDeck();
        slideDeck.addSlide(audioSlide);

        sendMediaMessage(forceSms, "", slideDeck, inputPanel.getQuote().orNull(), Collections.emptyList(), Collections.emptyList(), expiresIn, false, subscriptionId, initiating, true).addListener(new AssertedSuccessListener<Void>() {
          @Override
          public void onSuccess(Void nothing) {
            new AsyncTask<Void, Void, Void>() {
              @Override
              protected Void doInBackground(Void... params) {
                BlobProvider.getInstance().delete(ConversationActivity.this, result.first);
                return null;
              }
            }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
          }
        });
      }

      @Override
      public void onFailure(ExecutionException e) {
        Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_unable_to_record_audio, Toast.LENGTH_LONG).show();
      }
    });
  }

  @Override
  public void onRecorderCanceled() {
    updateToggleButtonState();
    Vibrator vibrator = ServiceUtil.getVibrator(this);
    vibrator.vibrate(50);

    getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

    ListenableFuture<Pair<Uri, Long>> future = audioRecorder.stopRecording();
    future.addListener(new ListenableFuture.Listener<Pair<Uri, Long>>() {
      @Override
      public void onSuccess(final Pair<Uri, Long> result) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            BlobProvider.getInstance().delete(ConversationActivity.this, result.first);
            return null;
          }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
      }

      @Override
      public void onFailure(ExecutionException e) {}
    });
  }

  @Override
  public void onEmojiToggle() {
    if (!emojiDrawerStub.resolved()) {
      Boolean stickersAvailable = stickerViewModel.getStickersAvailability().getValue();

      initializeMediaKeyboardProviders(emojiDrawerStub.get(), stickersAvailable == null ? false : stickersAvailable);

      inputPanel.setMediaKeyboard(emojiDrawerStub.get());
    }

    if (container.getCurrentInput() == emojiDrawerStub.get()) {
      container.showSoftkey(composeText);
    } else {
      container.show(composeText, emojiDrawerStub.get());
    }
  }

  @Override
  public void onLinkPreviewCanceled() {
    linkPreviewViewModel.onUserCancel();
  }

  @Override
  public void onStickerSuggestionSelected(@NonNull StickerRecord sticker) {
    sendSticker(sticker, true);
  }

  @Override
  public void onMediaSelected(@NonNull Uri uri, String contentType) {
    if (!TextUtils.isEmpty(contentType) && contentType.trim().equals("image/gif")) {
      setMedia(uri, MediaType.GIF);
    } else if (MediaUtil.isImageType(contentType)) {
      setMedia(uri, MediaType.IMAGE);
    } else if (MediaUtil.isVideoType(contentType)) {
      setMedia(uri, MediaType.VIDEO);
    } else if (MediaUtil.isAudioType(contentType)) {
      setMedia(uri, MediaType.AUDIO);
    }
  }

  @Override
  public void onCursorPositionChanged(int start, int end) {
    linkPreviewViewModel.onTextChanged(this, composeText.getTextTrimmed(), start, end);
  }

  @Override
  public void onStickerSelected(@NonNull StickerRecord stickerRecord) {
    sendSticker(stickerRecord, false);
  }

  @Override
  public void onStickerManagementClicked() {
    startActivity(StickerManagementActivity.getIntent(this));
    container.hideAttachedInput(true);
  }

  private void sendSticker(@NonNull StickerRecord stickerRecord, boolean clearCompose) {
    sendSticker(new StickerLocator(stickerRecord.getPackId(), stickerRecord.getPackKey(), stickerRecord.getStickerId()), stickerRecord.getUri(), stickerRecord.getSize(), clearCompose);

    SignalExecutors.BOUNDED.execute(() ->
     DatabaseFactory.getStickerDatabase(getApplicationContext())
                    .updateStickerLastUsedTime(stickerRecord.getRowId(), System.currentTimeMillis())
    );
  }

  private void sendSticker(@NonNull StickerLocator stickerLocator, @NonNull Uri uri, long size, boolean clearCompose) {
    if (sendButton.getSelectedTransport().isSms()) {
      Media  media  = new Media(uri, MediaUtil.IMAGE_WEBP, System.currentTimeMillis(), StickerSlide.WIDTH, StickerSlide.HEIGHT, size, Optional.absent(), Optional.absent());
      Intent intent = MediaSendActivity.buildEditorIntent(this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport());
      startActivityForResult(intent, MEDIA_SENDER);
      return;
    }

    long            expiresIn      = recipient.get().getExpireMessages() * 1000L;
    int             subscriptionId = sendButton.getSelectedTransport().getSimSubscriptionId().or(-1);
    boolean         initiating     = threadId == -1;
    TransportOption transport      = sendButton.getSelectedTransport();
    SlideDeck       slideDeck      = new SlideDeck();
    Slide           stickerSlide   = new StickerSlide(this, uri, size, stickerLocator);

    slideDeck.addSlide(stickerSlide);

    sendMediaMessage(transport.isSms(), "", slideDeck, null, Collections.emptyList(), Collections.emptyList(), expiresIn, false, subscriptionId, initiating, clearCompose);

  }

  private void silentlySetComposeText(String text) {
    typingTextWatcher.setEnabled(false);
    composeText.setText(text);
    typingTextWatcher.setEnabled(true);
  }

  // Listeners

  private class AttachmentTypeListener implements AttachmentTypeSelector.AttachmentClickedListener {
    @Override
    public void onClick(int type) {
      addAttachment(type);
    }

    @Override
    public void onQuickAttachment(Uri uri, String mimeType, String bucketId, long dateTaken, int width, int height, long size) {
      linkPreviewViewModel.onUserCancel();
      Media media = new Media(uri, mimeType, dateTaken, width, height, size, Optional.of(Media.ALL_MEDIA_BUCKET_ID), Optional.absent());
      startActivityForResult(MediaSendActivity.buildEditorIntent(ConversationActivity.this, Collections.singletonList(media), recipient.get(), composeText.getTextTrimmed(), sendButton.getSelectedTransport()), MEDIA_SENDER);
    }
  }

  private class QuickCameraToggleListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      Permissions.with(ConversationActivity.this)
                 .request(Manifest.permission.CAMERA)
                 .ifNecessary()
                 .withRationaleDialog(getString(R.string.ConversationActivity_to_capture_photos_and_video_allow_signal_access_to_the_camera), R.drawable.ic_camera_solid_24)
                 .withPermanentDenialDialog(getString(R.string.ConversationActivity_signal_needs_the_camera_permission_to_take_photos_or_video))
                 .onAllGranted(() -> {
                   composeText.clearFocus();
                   startActivityForResult(MediaSendActivity.buildCameraIntent(ConversationActivity.this, recipient.get(), sendButton.getSelectedTransport()), MEDIA_SENDER);
                   overridePendingTransition(R.anim.camera_slide_from_bottom, R.anim.stationary);
                 })
                 .onAnyDenied(() -> Toast.makeText(ConversationActivity.this, R.string.ConversationActivity_signal_needs_camera_permissions_to_take_photos_or_video, Toast.LENGTH_LONG).show())
                 .execute();
    }
  }

  private class SendButtonListener implements OnClickListener, TextView.OnEditorActionListener {
    @Override
    public void onClick(View v) {
      sendMessage();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_SEND) {
        sendButton.performClick();
        return true;
      }
      return false;
    }
  }

  private class AttachButtonListener implements OnClickListener {
    @Override
    public void onClick(View v) {
      handleAddAttachment();
    }
  }

  private class AttachButtonLongClickListener implements View.OnLongClickListener {
    @Override
    public boolean onLongClick(View v) {
      return sendButton.performLongClick();
    }
  }

  private class ComposeKeyPressedListener implements OnKeyListener, OnClickListener, TextWatcher, OnFocusChangeListener {

    int beforeLength;

    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
      if (event.getAction() == KeyEvent.ACTION_DOWN) {
        if (keyCode == KeyEvent.KEYCODE_ENTER) {
          if (TextSecurePreferences.isEnterSendsEnabled(ConversationActivity.this)) {
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            sendButton.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER));
            return true;
          }
        }
      }
      return false;
    }

    @Override
    public void onClick(View v) {
      container.showSoftkey(composeText);
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count,int after) {
      beforeLength = composeText.getTextTrimmed().length();
    }

    @Override
    public void afterTextChanged(Editable s) {
      calculateCharactersRemaining();

      if (composeText.getTextTrimmed().length() == 0 || beforeLength == 0) {
        composeText.postDelayed(ConversationActivity.this::updateToggleButtonState, 50);
      }

      stickerViewModel.onInputTextUpdated(s.toString());
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before,int count) {}

    @Override
    public void onFocusChange(View v, boolean hasFocus) {}
  }

  private class TypingStatusTextWatcher extends SimpleTextWatcher {

    private boolean enabled = true;

    @Override
    public void onTextChanged(String text) {
      if (enabled && threadId > 0 && isSecureText && !isSmsForced()) {
        ApplicationContext.getInstance(ConversationActivity.this).getTypingStatusSender().onTypingStarted(threadId);
      }
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  @Override
  public void onMessageRequest() {
    long        threadId    = getIntent().getLongExtra(THREAD_ID_EXTRA, -1);
    RecipientId recipientId = getIntent().getParcelableExtra(RECIPIENT_EXTRA);

    if (threadId == -1) {
      throw new IllegalStateException("MessageRequest is not supported here");
    }

    if (recipientId == null) {
      Log.w(TAG, "onMessageRequest: " + threadId + ": null recipient. finishing...");
      finish();
    }

    Log.i(TAG, "onMessageRequest: " + threadId + ", " + recipientId.serialize());

    MessageRequestFragmentViewModel.Factory factory   = new MessageRequestFragmentViewModel.Factory(this, threadId, recipientId);
    MessageRequestFragmentViewModel         viewModel = ViewModelProviders.of(this, factory).get(MessageRequestFragmentViewModel.class);
    MessageRequestFragment                  fragment  = new MessageRequestFragment();

    messageRequestOverlay.setVisibility(View.VISIBLE);
    container.setVisibility(View.GONE);
    getSupportFragmentManager().beginTransaction()
                               .add(R.id.fragment_overlay_container, fragment)
                               .commit();

    viewModel.getState().observe(this, state -> {
      switch (state.messageRequestState) {
        case ACCEPTED:
          getSupportFragmentManager().popBackStack();
          messageRequestOverlay.setVisibility(View.GONE);
          container.setVisibility(View.VISIBLE);
          return;
        case DELETED:
        case BLOCKED:
          finish();
      }
    });
  }

  public void handleReaction(@NonNull View maskTarget,
                             @NonNull MessageRecord messageRecord,
                             @NonNull Toolbar.OnMenuItemClickListener toolbarListener,
                             @NonNull ConversationReactionOverlay.OnHideListener onHideListener)
  {
    reactionOverlay.setOnToolbarItemClickedListener(toolbarListener);
    reactionOverlay.setOnHideListener(onHideListener);
    reactionOverlay.show(this, maskTarget, messageRecord);
  }

  @Override
  public void onCursorChanged() {
    if (!reactionOverlay.isShowing()) {
      return;
    }

    SimpleTask.run(() -> {
          //noinspection CodeBlock2Expr
          return DatabaseFactory.getMmsSmsDatabase(this)
                                .checkMessageExists(reactionOverlay.getMessageRecord());
        }, messageExists -> {
          if (!messageExists) {
            reactionOverlay.hide();
          }
        });
  }

  @Override
  public void setThreadId(long threadId) {
    this.threadId = threadId;
  }

  @Override
  public void handleReplyMessage(MessageRecord messageRecord) {
    Recipient author;

    if (messageRecord.isOutgoing()) {
      author = Recipient.self();
    } else {
      author = messageRecord.getIndividualRecipient();
    }

    if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getSharedContacts().isEmpty()) {
      Contact   contact     = ((MmsMessageRecord) messageRecord).getSharedContacts().get(0);
      String    displayName = ContactUtil.getDisplayName(contact);
      String    body        = getString(R.string.ConversationActivity_quoted_contact_message, EmojiStrings.BUST_IN_SILHOUETTE, displayName);
      SlideDeck slideDeck   = new SlideDeck();

      if (contact.getAvatarAttachment() != null) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, contact.getAvatarAttachment()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          body,
                          slideDeck,
                          messageRecord.isViewOnce());

    } else if (messageRecord.isMms() && !((MmsMessageRecord) messageRecord).getLinkPreviews().isEmpty()) {
      LinkPreview linkPreview = ((MmsMessageRecord) messageRecord).getLinkPreviews().get(0);
      SlideDeck   slideDeck   = new SlideDeck();

      if (linkPreview.getThumbnail().isPresent()) {
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, linkPreview.getThumbnail().get()));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          messageRecord.getBody(),
                          slideDeck,
                          messageRecord.isViewOnce());
    } else {
      SlideDeck slideDeck = messageRecord.isMms() ? ((MmsMessageRecord) messageRecord).getSlideDeck() : new SlideDeck();

      if (messageRecord.isMms() && ((MmsMessageRecord) messageRecord).isViewOnce() && slideDeck.getSlides().size() > 0) {
        Attachment attachment = new TombstoneAttachment(slideDeck.getSlides().get(0).getContentType(), true);
        slideDeck = new SlideDeck();
        slideDeck.addSlide(MediaUtil.getSlideForAttachment(this, attachment));
      }

      inputPanel.setQuote(GlideApp.with(this),
                          messageRecord.getDateSent(),
                          author,
                          messageRecord.getBody(),
                          slideDeck,
                          messageRecord.isViewOnce());
    }

    inputPanel.clickOnComposeInput();
  }

  @Override
  public void onMessageActionToolbarOpened() {
    searchViewItem.collapseActionView();
  }

  @Override
  public void onForwardClicked() {
    inputPanel.clearQuote();
  }

  @Override
  public void onAttachmentChanged() {
    handleSecurityChange(isSecureText, isDefaultSms);
    updateToggleButtonState();
    updateLinkPreviewState();
  }

  private class UnverifiedDismissedListener implements UnverifiedBannerView.DismissListener {
    @Override
    public void onDismissed(final List<IdentityRecord> unverifiedIdentities) {
      final IdentityDatabase identityDatabase = DatabaseFactory.getIdentityDatabase(ConversationActivity.this);

      new AsyncTask<Void, Void, Void>() {
        @Override
        protected Void doInBackground(Void... params) {
          synchronized (SESSION_LOCK) {
            for (IdentityRecord identityRecord : unverifiedIdentities) {
              identityDatabase.setVerified(identityRecord.getRecipientId(),
                                           identityRecord.getIdentityKey(),
                                           VerifiedStatus.DEFAULT);
            }
          }

          return null;
        }

        @Override
        protected void onPostExecute(Void result) {
          initializeIdentityRecords();
        }
      }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
  }

  private class UnverifiedClickedListener implements UnverifiedBannerView.ClickListener {
    @Override
    public void onClicked(final List<IdentityRecord> unverifiedIdentities) {
      Log.i(TAG, "onClicked: " + unverifiedIdentities.size());
      if (unverifiedIdentities.size() == 1) {
        Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
        intent.putExtra(VerifyIdentityActivity.RECIPIENT_EXTRA, unverifiedIdentities.get(0).getRecipientId());
        intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(unverifiedIdentities.get(0).getIdentityKey()));
        intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);

        startActivity(intent);
      } else {
        String[] unverifiedNames = new String[unverifiedIdentities.size()];

        for (int i=0;i<unverifiedIdentities.size();i++) {
          unverifiedNames[i] = Recipient.resolved(unverifiedIdentities.get(i).getRecipientId()).toShortString(ConversationActivity.this);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(ConversationActivity.this);
        builder.setIconAttribute(R.attr.dialog_alert_icon);
        builder.setTitle("No longer verified");
        builder.setItems(unverifiedNames, (dialog, which) -> {
          Intent intent = new Intent(ConversationActivity.this, VerifyIdentityActivity.class);
          intent.putExtra(VerifyIdentityActivity.RECIPIENT_EXTRA, unverifiedIdentities.get(which).getRecipientId());
          intent.putExtra(VerifyIdentityActivity.IDENTITY_EXTRA, new IdentityKeyParcelable(unverifiedIdentities.get(which).getIdentityKey()));
          intent.putExtra(VerifyIdentityActivity.VERIFIED_EXTRA, false);

          startActivity(intent);
        });
        builder.show();
      }
    }
  }

  private class QuoteRestorationTask extends AsyncTask<Void, Void, MessageRecord> {

    private final String                  serialized;
    private final SettableFuture<Boolean> future;

    QuoteRestorationTask(@NonNull String serialized, @NonNull SettableFuture<Boolean> future) {
      this.serialized = serialized;
      this.future     = future;
    }

    @Override
    protected MessageRecord doInBackground(Void... voids) {
      QuoteId quoteId = QuoteId.deserialize(ConversationActivity.this, serialized);

      if (quoteId != null) {
        return DatabaseFactory.getMmsSmsDatabase(getApplicationContext()).getMessageFor(quoteId.getId(), quoteId.getAuthor());
      }

      return null;
    }

    @Override
    protected void onPostExecute(MessageRecord messageRecord) {
      if (messageRecord != null) {
        handleReplyMessage(messageRecord);
        future.set(true);
      } else {
        Log.e(TAG, "Failed to restore a quote from a draft. No matching message record.");
        future.set(false);
      }
    }
  }

  private void initializeScreenshotEvent() {
    final Context context = getApplicationContext();
    String text           = "I have captured a screenshot of this conversation";

    shotWatch = new ShotWatch(getContentResolver(), new ShotWatch.Listener() {
      @Override
      public void onScreenShotTaken(ScreenshotData data) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... params) {
            long newThreadId;
            int  subscriptionId = recipient.get().getDefaultSubscriptionId().or(-1);
            long expiresIn      = recipient.get().getExpireMessages() * 1000;

            if (recipient.get().isGroup()) {
              OutgoingMediaMessage message = new OutgoingMediaMessage(recipient.get(), text, new LinkedList<>(), System.currentTimeMillis(), subscriptionId, expiresIn, false,
                      2, null, new LinkedList<>(), new LinkedList<>(), new LinkedList<>(), new LinkedList<>());
              newThreadId = MessageSender.send(context, message, threadId, false, null);
            } else {
              OutgoingTextMessage message = new OutgoingTextMessage(recipient.get(), text, expiresIn, subscriptionId);
              newThreadId = MessageSender.send(context, message, threadId, false, null);
            }

            List<MarkedMessageInfo> messageIds = DatabaseFactory.getThreadDatabase(context).setRead(newThreadId, true);
            DatabaseFactory.getThreadDatabase(context).setLastSeen(newThreadId);

            MessageNotifier.updateNotification(context);
            MarkReadReceiver.process(context, messageIds);

            return null;
          }
        }.execute();
      }
    });
  }
}
