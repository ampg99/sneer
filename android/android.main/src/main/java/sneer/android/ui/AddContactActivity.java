package sneer.android.ui;

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import me.sneer.R;
import sneer.Party;

import static sneer.android.SneerAndroidSingleton.sneer;
import static sneer.android.utils.Puk.shareOwnPublicKey;

public class AddContactActivity extends Activity {

	private Party party;

	private EditText nicknameEdit;
	private TextView txtNotConnectedYet;
	private Button btnSendInvite;

	private String nickname;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_add_contact);

		nicknameEdit = (EditText) findViewById(R.id.nickname);
		txtNotConnectedYet = (TextView) findViewById(R.id.lbl_not_connected_yet);
		btnSendInvite = (Button) findViewById(R.id.btn_send_invite);
		btnSendInvite.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				final long inviteCode = sneer().generateContactInvite();
				// TODO: persist inviteCode
				shareOwnPublicKey(AddContactActivity.this, sneer().self(), inviteCode, nickname);
				finish();
			}
		});

		validationOnTextChanged(nicknameEdit);
	}

	private void validationOnTextChanged(final EditText editText) {
		editText.addTextChangedListener(new TextWatcher() {
			@Override
			public void afterTextChanged(Editable s) {
				nickname = nicknameEdit.getText().toString();
				String error = sneer().problemWithNewNickname(sneer().self().publicKey().current(), nickname);

				txtNotConnectedYet.setVisibility(View.GONE);
				btnSendInvite.setVisibility(View.GONE);

				if (nickname.length() == 0) return;

				if (error != null) {
					editText.setError("Already connected");
					return;
				}

				txtNotConnectedYet.setVisibility(View.VISIBLE);
				btnSendInvite.setVisibility(View.VISIBLE);
			}

			@Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
			@Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
		});
	}

}