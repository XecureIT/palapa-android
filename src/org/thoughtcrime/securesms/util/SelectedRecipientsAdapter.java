package org.thoughtcrime.securesms.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.whispersystems.libsignal.util.guava.Optional;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class SelectedRecipientsAdapter extends BaseAdapter {
  @NonNull  private Context                    context;
  @Nullable private OnRecipientDeletedListener onRecipientDeletedListener;
  @NonNull  private List<RecipientWrapper>     recipients;
  @Nullable private String                     owner;
  @Nullable private Collection<Recipient>      admins;

  public SelectedRecipientsAdapter(@NonNull Context context) {
    this(context, Collections.<Recipient>emptyList(), null, Collections.<Recipient>emptyList());
  }

  public SelectedRecipientsAdapter(@NonNull Context context,
                                   @NonNull Collection<Recipient> existingRecipients,
                                   @Nullable String existingOwner,
                                   @Nullable Collection<Recipient> existingAdmins)
  {
    this.context    = context;
    this.owner      = existingOwner;
    this.admins     = existingAdmins;
    this.recipients = wrapExistingMembers(existingRecipients, existingOwner, existingAdmins);
  }

  public void add(@NonNull Recipient recipient, boolean isPush) {
    if (!find(recipient).isPresent()) {
      RecipientWrapper wrapper = new RecipientWrapper(recipient, true, isPush, false, false);
      this.recipients.add(0, wrapper);
      notifyDataSetChanged();
    }
  }

  public void addAdmin(@NonNull Recipient recipient) {
    if (updateAdmins(recipient, true) != null) {
      notifyDataSetChanged();
    }
  }

  public Optional<RecipientWrapper> find(@NonNull Recipient recipient) {
    RecipientWrapper found = null;
    for (RecipientWrapper wrapper : recipients) {
      if (wrapper.getRecipient().equals(recipient)) found = wrapper;
    }
    return Optional.fromNullable(found);
  }

  public void remove(@NonNull Recipient recipient) {
    Optional<RecipientWrapper> match = find(recipient);
    if (match.isPresent()) {
      recipients.remove(match.get());
      if (admins != null) {
        admins.remove(match.get().getRecipient());
      }
      notifyDataSetChanged();
    }
  }

  public void removeAdmin(@NonNull Recipient recipient) {
    if (updateAdmins(recipient, false) != null) {
      notifyDataSetChanged();
    }
  }

  public Set<Recipient> getRecipients() {
    final Set<Recipient> recipientSet = new HashSet<>(recipients.size());
    for (RecipientWrapper wrapper : recipients) {
      recipientSet.add(wrapper.getRecipient());
    }
    return recipientSet;
  }

  @Override
  public int getCount() {
    return recipients.size();
  }

  public boolean hasNonPushMembers() {
    for (RecipientWrapper wrapper : recipients) {
      if (!wrapper.isPush()) return true;
    }
    return false;
  }

  @Override
  public Object getItem(int position) {
    return recipients.get(position);
  }

  @Override
  public long getItemId(int position) {
    return position;
  }

  @Override
  public View getView(final int position, View v, final ViewGroup parent) {
    if (v == null) {
      v = LayoutInflater.from(context).inflate(R.layout.selected_recipient_list_item, parent, false);
    }

    final RecipientWrapper rw         = (RecipientWrapper)getItem(position);
    final Recipient        p          = rw.getRecipient();
    final boolean          modifiable = false; // rw.isModifiable();
    final boolean          owner      = rw.isOwner();
    final boolean          admin      = rw.isAdmin();

    TextView    name   = (TextView)    v.findViewById(R.id.name);
    TextView    phone  = (TextView)    v.findViewById(R.id.phone);
    ImageButton delete = (ImageButton) v.findViewById(R.id.delete);
    ImageView badge    = (ImageView)   v.findViewById(R.id.badge);

    if (owner) {
      badge.setImageResource(R.drawable.ic_badge_owner);
    } else if (admin) {
      badge.setImageResource(R.drawable.ic_badge_admin);
    } else {
      badge.setImageResource(android.R.color.transparent);
    }

    name.setText(p.getDisplayName(v.getContext()));
    phone.setText(p.getE164().or(""));
    delete.setVisibility(modifiable ? View.VISIBLE : View.GONE);
    delete.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (onRecipientDeletedListener != null) {
          onRecipientDeletedListener.onRecipientDeleted(recipients.get(position).getRecipient());
        }
      }
    });

    return v;
  }

  private static List<RecipientWrapper> wrapExistingMembers(Collection<Recipient> recipients, String owner, Collection<Recipient> admins) {
    final LinkedList<RecipientWrapper> wrapperList = new LinkedList<>();
    for (Recipient recipient : recipients) {
      boolean isOwner = false;
      if (owner != null) isOwner = owner.equals(recipient.getE164().get());
      boolean isAdmin = false;
      if (admins != null) isAdmin = admins.contains(recipient);
      wrapperList.add(new RecipientWrapper(recipient, false, true, isOwner, isAdmin));
    }
    return wrapperList;
  }

  private RecipientWrapper updateAdmins(Recipient recipient, boolean isAdmin) {
    for (RecipientWrapper wrapper : recipients) {
      if (wrapper.getRecipient().equals(recipient)) {
        RecipientWrapper found = new RecipientWrapper(wrapper.getRecipient(), wrapper.isModifiable(), wrapper.isPush(), wrapper.isOwner(), isAdmin);
        if (admins != null) {
          if (isAdmin) {
            admins.add(recipient);
          } else {
            admins.remove(recipient);
          }
        }
        recipients.set(recipients.indexOf(wrapper), found);
        return found;
      }
    }
    return null;
  }

  public boolean isOwnerNumber(String number) {
    if (owner != null) {
      return number.equals(owner);
    }
    return false;
  }

  public boolean isAdminNumber(String number) {
    if (admins != null) {
      for (Recipient admin: admins) {
        if (number.equals(admin.getE164().get())) {
          return true;
        }
      }
    }
    return false;
  }

  public void setOnRecipientDeletedListener(@Nullable OnRecipientDeletedListener listener) {
    onRecipientDeletedListener = listener;
  }

  public interface OnRecipientDeletedListener {
    void onRecipientDeleted(Recipient recipient);
  }

  public static class RecipientWrapper {
    private final Recipient recipient;
    private final boolean   modifiable;
    private final boolean   push;
    private final boolean   owner;
    private final boolean   admin;

    public RecipientWrapper(final @NonNull Recipient recipient,
                            final boolean modifiable,
                            final boolean push, final boolean owner, final boolean admin)
    {
      this.recipient  = recipient;
      this.modifiable = modifiable;
      this.push       = push;
      this.owner      = owner;
      this.admin      = admin;
    }

    public @NonNull Recipient getRecipient() {
      return recipient;
    }

    public boolean isModifiable() {
      return modifiable;
    }

    public boolean isPush() {
      return push;
    }

    public boolean isOwner() {
      return owner;
    }

    public boolean isAdmin() {
      return admin;
    }
  }
}