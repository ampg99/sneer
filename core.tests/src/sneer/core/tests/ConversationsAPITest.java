package sneer.core.tests;

import static sneer.commons.Arrays.asList;
import static sneer.core.tests.ObservableTestUtils.eventually;
import static sneer.core.tests.ObservableTestUtils.expecting;
import static sneer.core.tests.ObservableTestUtils.payloads;
import static sneer.core.tests.ObservableTestUtils.same;
import static sneer.core.tests.ObservableTestUtils.values;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import rx.Observable;
import rx.functions.Func1;
import rx.observables.ConnectableObservable;
import rx.subjects.ReplaySubject;
import sneer.Contact;
import sneer.Conversation;
import sneer.Message;
import sneer.Party;
import sneer.PrivateKey;
import sneer.Profile;
import sneer.PublicKey;
import sneer.Sneer;
import sneer.admin.SneerAdmin;
import sneer.commons.Arrays;
import sneer.commons.Clock;
import sneer.commons.exceptions.FriendlyException;
import sneer.impl.keys.KeysImpl;
import sneer.tuples.Tuple;
import sneer.tuples.TuplePublisher;

public class ConversationsAPITest extends TestCase {
	
	protected final Object network = newNetwork();

	protected Object newNetwork() {
		return Glue.newNetworkSimulator();
	}

	@Override
	public void tearDown() {
		Glue.tearDownNetwork(network);
	}
	
	protected final Object tupleBaseA = newTupleBase();
	
	protected final SneerAdmin adminA = newSneerAdmin(new KeysImpl().createPrivateKey(), tupleBaseA);
	protected final SneerAdmin adminB = newSneerAdmin();
	protected final SneerAdmin adminC = newSneerAdmin();

	protected final PublicKey userA = adminA.sneer().self().publicKey().current();
	protected final PublicKey userB = adminB.sneer().self().publicKey().current();
	protected final PublicKey userC = adminC.sneer().self().publicKey().current();
	
	protected final Sneer sneerA = adminA.sneer();
	protected final Sneer sneerB = adminB.sneer();
	protected final Sneer sneerC = adminC.sneer();

	
	protected PrivateKey newPrivateKey() {
		return new KeysImpl().createPrivateKey();
	}
	
	private SneerAdmin newSneerAdmin() {
		return newSneerAdmin(new KeysImpl().createPrivateKey(), newTupleBase());
	}
	private SneerAdmin newSneerAdmin(PrivateKey prik, Object tupleBase) {
		return Glue.newSneerAdmin(prik, network, tupleBase);
	}

	protected Object newTupleBase() {
		return ReplaySubject.create();
	}


	public void testSameSneer() {
		assertEquals(adminA.sneer(), adminA.sneer());
	}


	public void testSameProfile() {
		assertEquals(sneerA.profileFor(sneerA.self()), sneerA.profileFor(sneerA.self()));
	}


	public void testPukOfParty() {

		Party someone = sneerA.produceParty(userB);

		assertEquals(userB, someone.publicKey().current());

	}

	public void testAlwaysReturnsSamePartyInstance() {

		Party someoneElse = sneerA.produceParty(userB);

		assertSame(someoneElse, sneerA.produceParty(userB));

	}

	public void testAddContact() throws FriendlyException {

		final Party partyB = sneerA.produceParty(userB);

		Contact contact = sneerA.findContact(partyB);
		assertNull(contact);
		
		sneerA.addContact("Party Boy", partyB);
		assertSame(partyB, sneerA.findContact(partyB).party());
	}

	
	public void testExceptionOnDuplicatedNickname() throws FriendlyException {

		sneerA.addContact("Party Boy", sneerA.produceParty(userB));
		try {
			sneerA.addContact("Party Boy", sneerA.produceParty(userC));
			fail("should have failed with "+FriendlyException.class.getSimpleName());
		} catch (FriendlyException expected) {}
	}

	
	public void testChangeContactNickname() throws FriendlyException {
		Party party = sneerA.produceParty(userB);

		sneerA.addContact("Party Boy", party);
		
		Observable<String> nicks = sneerA.findContact(party).nickname().observable();
		expecting(
			values(nicks, "Party Boy"));

		sneerA.findContact(party).setNickname("Party Man");
		expecting(
			values(nicks, "Party Man"));
	}

	
	public void testChangeContactNicknamePersistence() throws FriendlyException {
		Party party = sneerA.produceParty(userB);
		sneerA.addContact("Party Boy", party);
		
		Contact contact = sneerA.findContact(party);
		contact.setNickname("Party Man");

		Sneer newSneer = newSneerAdmin(adminA.privateKey(), tupleBaseA).sneer();
		Party newParty = newSneer.produceParty(userB);
		assertEquals("Party Man", newSneer.findContact(newParty).nickname().current());
	}

	
	public void testProblemWithNewNickname() throws FriendlyException {
		assertNotNull(sneerA.problemWithNewNickname(""));

		Party partyB = sneerA.produceParty(userB);
		Party partyC = sneerA.produceParty(userC);
		
		assertNull   (sneerA.problemWithNewNickname("Party Boy"));
		sneerA.addContact("Party Boy", partyB);
		assertNotNull(sneerA.problemWithNewNickname("Party Boy"));

		try {
			sneerA.addContact("Party Boy", partyC);
			fail();
		} catch (FriendlyException expected) {}

		try {
			sneerA.addContact("Party Boy2", partyB);
			fail();
		} catch (FriendlyException expected) {}

		sneerA.addContact("Party Chick", partyC);
		Contact chick = sneerA.findContact(partyC);
		try {
			chick.setNickname("Party Boy");
			fail();
		} catch (FriendlyException expected) {}
		
		chick.setNickname("Party Chick 2");
	}

	
	public void testContactListSequence() throws FriendlyException {

		final Party partyB = sneerA.produceParty(userB);
		final Party partyC = sneerA.produceParty(userC);
		
		expecting(
			values(sneerA.contacts(), Collections.emptyList()));
		
		sneerA.addContact("Party Boy", partyB);
		
		expecting(
			contactsOf(sneerA, partyB));
		
		sneerA.addContact("Party Chick", partyC);

		expecting(
			contactsOf(sneerA, partyB, partyC));
	}
	
	public void testContactListRestore() throws FriendlyException {

		final Party partyB = sneerA.produceParty(userB);
		final Party partyC = sneerA.produceParty(userC);
		sneerA.addContact("Party Boy", partyB);
		sneerA.addContact("Party Chick", partyC);

		expecting(
			contactsOf(restart(adminA).sneer(), partyB, partyC));
	}


	private Observable<Void> contactsOf(final Sneer sneer, final Party... parties) {
		return sneer.contacts().map(new Func1<List<Contact>, Void>() {  @Override public Void call(List<Contact> t1) {
			ObservableTestUtils.assertArrayEquals(
				Arrays.map(parties, new Func1<Party, Contact>() {  @Override public Contact call(Party t1) {
					return sneer.findContact(t1);
				}}),
				t1.toArray());
			return null;
		} });
	}
	
	public void testPreferredNickname() {
		
		Profile profileBFromB = sneerB.profileFor(sneerB.self());
		Profile profileBFromA = sneerA.profileFor(sneerA.produceParty(userB));
		
		profileBFromB.setPreferredNickname("Party Boy");
		
		expecting(
			values(profileBFromA.preferredNickname(), "Party Boy"));
		
		profileBFromB.setPreferredNickname("Party Man");

		expecting(
			eventually(profileBFromA.preferredNickname(), "Party Man"));
		
	}
	
	public void testPreferredNicknameForSelf() {
		
		Profile profileB = sneerB.profileFor(sneerB.self());
		profileB.setPreferredNickname("Party Boy");		
		expecting(
			values(profileB.preferredNickname(), "Party Boy"));
		
		SneerAdmin adminB2 = restart(adminB);
		Sneer sneerB2 = adminB2.sneer();
		Profile profileB2 = sneerB2.profileFor(sneerB2.self());
		expecting(
			values(profileB2.preferredNickname(), "Party Boy"));
		
		profileB2.setPreferredNickname("Party Man");
		expecting(
			eventually(profileB2.preferredNickname(), "Party Man"));
		
	}	
	
	
	public void testIsOwnNameLocallyAvailable() {
		
		Party self = sneerA.self();
		Profile profileForSelf = sneerA.profileFor(self);
		
		assertEquals(false, profileForSelf.isOwnNameLocallyAvailable());
		
		profileForSelf.setOwnName("neide");
		
		assertEquals(true, profileForSelf.isOwnNameLocallyAvailable());
	}
	
	
	public void testTuplesFromContactsAreVisible() throws FriendlyException {
		
		sneerA.addContact("little b", sneerA.produceParty(userB));
		
		sneerB.tupleSpace().publisher()
			.type("tweet")
			.pub("hello");
		
		expecting(payloads(sneerA.tupleSpace().filter().type("tweet").tuples(), "hello"));
		
	}

	public void testTuplesFromNewContactsAreVisible() throws FriendlyException {
		
		// open twitter client
		ConnectableObservable<Tuple> tweets = sneerA.tupleSpace().filter().type("tweet").tuples().replay();
		tweets.connect();
		
		// future contact publishes a tweet
		sneerB.tupleSpace()
			.publisher()
			.type("tweet")
			.pub("hello");
		
		// it becomes a contact
		sneerA.addContact("little b", sneerA.produceParty(userB));
		
		// tweets should be visible
		expecting(payloads(tweets, "hello"));
		
	}
	
	public void testEmitConversationForEveryContact() throws FriendlyException {

		Party partyBOfA = sneerA.produceParty(userB);
		sneerA.addContact("little b", partyBOfA);

		expecting(
			same(
				flatMapConversationsOf(sneerA).map(new Func1<Conversation, Party>() {  @Override public Party call(Conversation t1) {
					return t1.party();
				}}), 
				partyBOfA));
		
	}

	private Observable<Conversation> flatMapConversationsOf(Sneer sneer) {
		return sneer.conversations()
			.flatMapIterable(new Func1<List<Conversation>, Iterable<? extends Conversation>>() {  @Override public Iterable<? extends Conversation> call(List<Conversation> t1) {
				return t1;
			} });
	}
	
	
	public void testConversationMessageSequence() throws Exception {
		
		Party pAB = sneerA.produceParty(userB);
		sneerA.addContact("b", pAB);
		Conversation cAB = sneerA.produceConversationWith(pAB);
		
		Party pBA = sneerB.produceParty(userA);
		sneerB.addContact("a", pBA);
		Conversation cBA = sneerB.produceConversationWith(pBA);
		
		Clock.mock();
		cAB.sendMessage("Hello1");
		messagesEventually(cBA, "Hello1");
		Clock.tick();
		cBA.sendMessage("Hello2");
		messagesEventually(cAB, "Hello1", "Hello2");
		Clock.tick();
		cAB.sendMessage("Hello3");
		messagesEventually(cBA, "Hello1", "Hello2", "Hello3");
		
		//Restart
		Sneer newSneer = newSneerAdmin(adminA.privateKey(), tupleBaseA).sneer();
		Party newB = newSneer.produceParty(userB);
		Conversation newConvo = sneerA.produceConversationWith(newB);
		messagesEventually(newConvo, "Hello1", "Hello2", "Hello3");
	}

	
	private void messagesEventually(Conversation convo, String... msgsExpected) {
		expecting(eventually(convo.messages().map(toMessageContentList()), asList(msgsExpected)));
	}

	
	public void testPartyName() throws FriendlyException {
		
		// 1 - type=contact party=puk
		// 2 - ? profile/preferred-nickname author=puk
		// 3 - ? profile/preferred-name author=puk
		// 3 - puk
		
		// TODO
		
		Party partyBOfA = sneerA.produceParty(userB);
		sneerA.addContact("little b", partyBOfA);
		
		expecting(
			values(partyBOfA.name(), "little b"));
		
	}
	
	public void testMessageLabel() {
		TuplePublisher publisher = sneerA.tupleSpace().publisher()
			.field("conversation?", true)
			.audience(userB)			
			.type("otherType");
		
		publisher.field("label", "mylabel").pub("bla");
		publisher.field("label", "test").pub("bla");
		publisher.type("message").pub("bla");
		
		Observable<String> contents = sneerA
			.produceConversationWith(sneerA.produceParty(userB))
			.messages()
			.flatMapIterable(new Func1<List<Message>, Iterable<? extends Message>>() {  @Override public Iterable<? extends Message> call(List<Message> messages) {
				return messages;
			}})
			.map(new Func1<Message, String>() {  @Override public String call(Message message) {
				return message.content().toString();
			}});
		
		expecting(values(contents, "mylabel", "test", "bla"));
	}
	
	
	private Func1<? super List<Message>, ? extends List<Object>> toMessageContentList() {
		return new Func1<List<Message>, List<Object>>() {  @Override public List<Object> call(List<Message> t1) {
			ArrayList<Object> r = new ArrayList<Object>(t1.size());
			for (Message m : t1) r.add(m.content());
			return r;
		}};
	}

	protected Func1<List<?>,Boolean> isEmpty() {
		return new Func1<List<?>, Boolean>() { @Override public Boolean call(List<?> t1) {
			return t1.isEmpty();
		}};
	}

	
	private SneerAdmin restart(SneerAdmin admin) {
		return Glue.restart(admin);
	}	
}
