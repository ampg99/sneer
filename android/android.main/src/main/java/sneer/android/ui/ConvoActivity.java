package sneer.android.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.Html;
import android.text.Spannable;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.functions.Action1;
import sneer.convos.ChatMessage;
import sneer.convos.Convo;
import sneer.convos.Convos;
import sneer.main.R;

import static sneer.android.SneerAndroidContainer.component;
import static sneer.android.SneerAndroidFlux.dispatch;
import static sneer.android.ui.ContactActivity.CURRENT_NICKNAME;

public class ConvoActivity extends SneerActionBarActivity {
	private static final String ACTIVITY_TITLE = "activityTitle";

	private Observable<Convo> convoObservable;
	private Subscription convoSubscription;
	private Convo currentConvo;

	private ChatAdapter chatAdapter;

	private ActionBar actionBar;
	private ImageButton messageButton;
	private EditText messageInput;


	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		setContentView(R.layout.activity_conversation);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);	 // Attaching the layout to the toolbar object
		setSupportActionBar(toolbar);							   // Setting toolbar as the ActionBar with setSupportActionBar() call

		actionBar = getSupportActionBar();
		actionBar.setDisplayHomeAsUpEnabled(true);
		actionBar.setDisplayShowTitleEnabled(true);

		// TODO Register a Click Listener on the Toolbar Title via reflection. Find a better solution
		try {
			Field titleField = Toolbar.class.getDeclaredField("mTitleTextView");
			titleField.setAccessible(true);
			TextView barTitleView = (TextView) titleField.get(toolbar);
			barTitleView.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
				onToolbarTitleClick();
			}});
		} catch (NoSuchFieldException e) {
			// Ignore
		} catch (IllegalAccessException e) {
			// Ignore
		}

		long convoId = getIntent().getLongExtra("id", -1);
		convoObservable = component(Convos.class).getById(convoId);

		chatAdapter = new ChatAdapter(this, this.getLayoutInflater());
		((ListView)findViewById(R.id.messageList)).setAdapter(chatAdapter);

		setupMessageFields();
	}


	private void refresh() {
		actionBar.setTitle(currentConvo.nickname);
		refreshInvitePendingMessage();
		chatAdapter.update(currentConvo.nickname, currentConvo.messages);

		ChatMessage last = lastMessageReceived(currentConvo.messages);
		if (last != null)
			dispatch(last.setRead());
	}


	private void refreshInvitePendingMessage() {
		boolean pending = currentConvo.inviteCodePending != null;
		messageInput.setEnabled(!pending);
		messageButton.setEnabled(!pending);

		final TextView waiting = (TextView) findViewById(R.id.waitingMessage);
		final ListView messageList = (ListView) findViewById(R.id.messageList);
		if (pending) {
			String waitingMessage = ConvoActivity.this.getResources().getString(R.string.conversation_activity_waiting);
			waiting.setText(Html.fromHtml(String.format(waitingMessage, currentConvo.nickname)));
			waiting.setMovementMethod(new LinkMovementMethod() {
				@Override
				public boolean onTouchEvent(@NonNull TextView widget, @NonNull Spannable buffer, @NonNull MotionEvent event) {
					// TODO Restore
					// if (event.getAction() == MotionEvent.ACTION_UP)
					//	shareOwnPublicKey(ConvoActivity.this, sneer().self(), contact.inviteCode(), contact.nickname().current());
					return true;
				}
			});
			messageList.setVisibility(View.GONE);
		} else {
			waiting.setVisibility(View.GONE);
			messageList.setVisibility(View.VISIBLE);
		}
	}


	private void setupMessageFields() {
		messageInput = (EditText) findViewById(R.id.editText);
		messageInput.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				messageButton.setImageResource(messageInput.getText().toString().trim().isEmpty()
				? R.drawable.ic_action_new
				: R.drawable.ic_action_send);
			}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});

		messageInput.setOnKeyListener(new OnKeyListener() { @Override public boolean onKey(View v, int keyCode, KeyEvent event) {
			if (!isHardwareKeyboardAvailable()) return false;
			if (!(event.getAction() == KeyEvent.ACTION_DOWN)) return false;
			if (!(keyCode == KeyEvent.KEYCODE_ENTER)) return false;
			sendMessageClicked();
			return true;
		}});

		messageButton = (ImageButton)findViewById(R.id.actionButton);

		messageButton.setImageResource(R.drawable.ic_action_new);
		messageButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				sendMessageClicked();
			}
		});
	}


	private void sendMessageClicked() {
		if (currentConvo == null) return;

		String text = messageInput.getText().toString().trim();

		if (!text.isEmpty()) {
			dispatch(currentConvo.sendMessage(text));
			messageInput.setText("");
		} else
			openInteractionMenu();
	}


	private void openInteractionMenu() {
		StartPluginDialogFragment startPluginDialog = new StartPluginDialogFragment();
		startPluginDialog.show(getFragmentManager(), "StartPluginDialogFrament");
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.title:
				navigateToContact();
				return true;
		}
		return super.onOptionsItemSelected(item);
	}


	public void onToolbarTitleClick() {
		navigateToContact();
	}


	private void navigateToContact() {
		Intent intent = new Intent();
		intent.setClass(this, ContactActivity.class);
		intent.putExtra(CURRENT_NICKNAME, currentConvo.nickname);
		intent.putExtra(ACTIVITY_TITLE, "Contact");
		startActivity(intent);
	}


	private void hideKeyboard() {
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(messageInput.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
	}


	private boolean isHardwareKeyboardAvailable() {
		return getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS;
	}


	@Override
	protected void onPause() {
		unsubscribeToConvo();
		super.onPause();
		// TODO Restore sneer().conversations().notificationsStopIgnoring();
	}


	@Override
	protected void onResume() {
		super.onResume();
		hideKeyboard();
		subscribeToConvo();
		// TODO Restore sneer().conversations().notificationsStartIgnoring(conversation);
	}


	private void subscribeToConvo() {
		if (convoSubscription != null) return;
		convoSubscription = ui(convoObservable).subscribe(new Action1<Convo>() {
			@Override
			public void call(Convo convo) {
				currentConvo = convo;
				refresh();
			}
		});
	}


	private void unsubscribeToConvo() {
		convoSubscription.unsubscribe();
		currentConvo = null;
	}


	private ChatMessage lastMessageReceived(List<ChatMessage> msgs) {
		for (int i = msgs.size() - 1; i >= 0; --i) {
			ChatMessage message = msgs.get(i);
			if (!message.isOwn)
				return message;
		}
		return null;
	}

}
