package sneer.android.main.ui;

import sneer.*;
import sneer.android.main.*;
import android.app.*;
import android.content.*;
import android.view.*;
import android.widget.*;

public class ContactAddHelper {
	
	public interface AddListener {
		void add(Contact contact);
	}
	
	public ContactAddHelper(InteractionListActivity context, final AddListener addListener) {
		View addContactView = View.inflate(context, R.layout.activity_contact_add, null);
		final EditText publicKeyEdit = (EditText) addContactView.findViewById(R.id.public_key);
		final EditText nicknameEdit = (EditText) addContactView.findViewById(R.id.nickname);
		AlertDialog alertDialog = new AlertDialog.Builder(context)
			.setView(addContactView)
			.setTitle(R.string.action_add_contact)
			.setNegativeButton("Cancel", null)
			.setPositiveButton("Add", new DialogInterface.OnClickListener() { public void onClick(DialogInterface dialog, int id) {
				//addListener.add(new Contact(publicKeyEdit.getText().toString(), nicknameEdit.getText().toString()));
			}})
			.create();
		alertDialog.show();
	}

}
