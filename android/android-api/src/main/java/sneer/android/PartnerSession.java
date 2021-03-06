package sneer.android;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.widget.Toast;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import sneer.android.impl.Envelope;
import sneer.android.impl.IPCProtocol;
import sneer.android.ui.SneerInstallation;

import static android.content.Context.BIND_AUTO_CREATE;
import static android.content.Context.BIND_IMPORTANT;
import static android.os.Message.obtain;
import static sneer.android.impl.Envelope.envelope;
import static sneer.android.impl.IPCProtocol.ENVELOPE;
import static sneer.android.impl.IPCProtocol.IS_OWN;

public class PartnerSession implements Closeable {

    public static PartnerSession join(Activity activity, Listener listener) {
        return join(activity, activity.getIntent(), listener);
    }
    /** @param intent The intent started by Sneer for an app's activity. */
    public static PartnerSession join(Context context, Intent intent, Listener listener) {
        return new PartnerSession(context, intent, listener);
    }

	public boolean wasStartedByMe() {
		if (!intent.hasExtra(IS_OWN)) throw new IllegalStateException("Unable to determine who started the session.");
		return intent.getBooleanExtra(IS_OWN, false);
	}

	public void send(Object payload) {
		await(connectionPending);
		sendToSneer(payload);
	}

	public interface Listener {
		void onUpToDate();
		void onMessage(Message message);
    }


	@Override
	public void close() {
		if (wasBound) context.unbindService(connection);
	}


	private final Context context;
    private Intent intent;
    private final Listener listener;
	private final CountDownLatch connectionPending = new CountDownLatch(1);
	private final ServiceConnection connection = createConnection();

	private Messenger toSneer;
    private boolean wasBound;
    private boolean isUpToDate = false;


	private ServiceConnection createConnection() {
		return new ServiceConnection() {
			@Override
			public void onServiceConnected(ComponentName name, IBinder service) {
				toSneer = new Messenger(service);
				Messenger callback = new Messenger(new FromSneerHandler(PartnerSession.this));
				sendToSneer(callback);
				connectionPending.countDown();
			}

			@Override
			public void onServiceDisconnected(ComponentName name) {
				toSneer = null;
				finish("Connection to Sneer was lost");
				connectionPending.countDown();
			}
		};
	}


    private PartnerSession(Context context_, Intent intent_, Listener listener) {
		this.context = context_;
        this.intent = intent_;
        this.listener = listener;

	    Intent sneer = intent.getParcelableExtra(IPCProtocol.JOIN_SESSION);
	    if (sneer == null) {
            handleSneerNotfound();
            return;
	    }
		wasBound = context.bindService(sneer, connection, BIND_AUTO_CREATE | BIND_IMPORTANT);
		if (!wasBound) finish("Unable to connect to Sneer");
    }


    private void handleSneerNotfound() {
        String message = context.getClass().getSimpleName() + ": Make sure Sneer session metadata is correctly set in your AndroidManifest.xml file";

        if (context instanceof Activity) {
            if (SneerInstallation.checkConversationContext((Activity)context))
                finish(message);
        } else
            throw new IllegalStateException(message);
    }


    private void finish(String endingToast) {
		toast(endingToast);
        if (context instanceof Activity)
            ((Activity)context).finish();
	}


	void handleMessageFromSneer(android.os.Message msg) {
		Bundle data = msg.getData();
		data.setClassLoader(getClass().getClassLoader());
		Object content = ((Envelope)data.getParcelable(ENVELOPE)).content;
		if (content.equals(IPCProtocol.UP_TO_DATE))
			isUpToDate = true;
		else
			listener.onMessage(getMessage(content));

		if (isUpToDate)
            handleUpToDate();
	}

    private void handleUpToDate() {
        Runnable runnable = new Runnable() { @Override public void run() {
            listener.onUpToDate();
        }};
        if (context instanceof Activity)
            ((Activity)context).runOnUiThread(runnable);
        else
            runnable.run();
    }


    private void sendToSneer(Object data) {
		android.os.Message msg = asMessage(data);
		try {
			doSendToSneer(msg);
		} catch (Exception e) {
			handleException(e);
		}
	}


	private android.os.Message asMessage(Object data) {
		android.os.Message ret = obtain();
		Bundle bundle = new Bundle();
		bundle.putParcelable(ENVELOPE, envelope(data));
		ret.setData(bundle);
		return ret;
	}


	private void doSendToSneer(android.os.Message msg) throws Exception {
		if (toSneer == null) {
			toast("No connection to Sneer");
			return;
		}
		toSneer.send(msg);
	}


	private Message getMessage(Object content) {
		@SuppressWarnings("unchecked")
		final Map<String, Object> map = (Map<String, Object>)content;
		return new Message() {
			@Override
			public boolean wasSentByMe() {
				return (Boolean)map.get(IS_OWN);
			}

			@Override
			public Object payload() {
				return map.get(IPCProtocol.PAYLOAD);
			}
		};
	}


	private void handleException(Exception e) {
		e.printStackTrace();
		toast(e.getMessage());
	}


	private void toast(String message) {
		Toast.makeText(context, message, Toast.LENGTH_LONG).show();
	}


	private static void await (CountDownLatch latch) {
		try { latch.await(); } catch (InterruptedException e) { throw new RuntimeException(e); }
	}

}

/** Separate class because ADT will issue HandlerLeak warnings if a Handler is an inner class. Suppressing it doesn't work. */
class FromSneerHandler extends Handler {
	private final PartnerSession session;

	FromSneerHandler(PartnerSession session) {
		this.session = session;
	}

	@Override
	public void handleMessage(android.os.Message msg) {
		session.handleMessageFromSneer(msg);
	}
}
