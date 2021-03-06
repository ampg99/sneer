(ns sneer.contact
  (:require
    [rx.lang.clojure.core :as rx]
    [sneer.rx :refer [atom->obs combine-latest]]
    [sneer.party :refer [party->puk produce-party!]]
    [sneer.party-impl :refer [name-subject]])
  (:import
    [java.util UUID]
    [sneer Contact]
    [sneer.rx ObservedSubject]
    [sneer.commons.exceptions FriendlyException]
    [sneer.tuples Tuple TupleSpace]
    [rx Observable]))

(defn publish-contact [tuple-space own-puk new-nick party invite-code]
  (.. ^TupleSpace tuple-space
      publisher
      (audience own-puk)
      (type "contact")
      (field "party" (when party (party->puk party)))
      (field "invite-code" invite-code)
      (pub new-nick)))

(defn problem-with-new-nickname-in [nick->contact puk->contact new-nick party]
  (let [old-by-nick (get nick->contact new-nick)
        old-by-puk (get puk->contact (party->puk party))]
    (cond
      (.isEmpty ^String new-nick)
      "cannot be empty"

      (and old-by-puk
           old-by-nick
           (not= old-by-puk old-by-nick))
      "party already is a contact"

      (and old-by-nick (= party (-> old-by-nick .party .current)))
      nil

      (some-> old-by-nick .party .current)
      "already used")))

(defn problem-with-new-nickname [contacts-state new-nick party]
  (problem-with-new-nickname-in @(:nick->contact contacts-state)
                                @(:puk->contact  contacts-state)
                                new-nick
                                party))

(defn check-new-nickname [nick->contact puk->contact new-nick party]
  (when-let [problem (problem-with-new-nickname-in nick->contact puk->contact new-nick party)]
    (throw (FriendlyException. (str "Nickname " problem)))))

(defn reify-contact
  [tuple-space nick->contact-atom puk->contact-atom own-puk nickname party invite-code]
  (let [nick-subject (ObservedSubject/create nickname)
        party-subject (ObservedSubject/create party)]
    (when party
      (.subscribe ^Observable (.observable nick-subject) ^ObservedSubject (name-subject party)))
    (reify Contact
      (party [_] (.observed party-subject))

      (setParty [this party]
        (when (.current party-subject)
          (throw (FriendlyException. "This contact already has a party.")))
        (when (get @puk->contact-atom (.publicKey party))
          (throw (FriendlyException. "Another contact already has this party.")))
        (publish-contact tuple-space own-puk nickname party nil)
        (swap! puk->contact-atom #(-> %
                                      (dissoc (.. this nickname current))
                                      (assoc (.. party publicKey current) this)))
        (.onNext party-subject party))

      (inviteCode [_] invite-code)

      (nickname [_]
        (.observed nick-subject))

      (setNickname [this new-nick]
        (when party
          (check-new-nickname @nick->contact-atom @puk->contact-atom new-nick party))
        (publish-contact tuple-space own-puk new-nick party invite-code)
        (swap! nick->contact-atom #(-> %
                                       (dissoc (.. this nickname current))
                                       (assoc new-nick this)))
        (rx/on-next nick-subject new-nick))

      (toString [this]
        (str "#<Contact " (.current nick-subject) ">")))))

(defn tuple->contact [tuple-space nick->contact puk->contact ^Tuple tuple puk->party]
  (reify-contact tuple-space
                 nick->contact
                 puk->contact
                 (.author tuple)
                 (.payload tuple)
                 (some->> (.get tuple "party") (produce-party! puk->party))
                 (.get tuple "invite-code")))

(defn restore-contacts [^TupleSpace tuple-space nick->contact puk->contact own-puk puk->party]
  (->>
    (.. tuple-space
        filter
        (author own-puk)
        (type "contact")
        localTuples
        toBlocking
        toIterable)
    #_(mapcat (fn [^Tuple tuple]
              [(.get tuple "party") (tuple->contact tuple-space puk->contact tuple puk->party)]))
    #_(apply hash-map)
    (map (fn [^Tuple tuple]
              (tuple->contact tuple-space nick->contact puk->contact tuple puk->party)))))

(defn current-nickname [^Contact contact]
  (.. contact nickname current))

(defn- ->contact-list [nick->contact]
  (->> (vals nick->contact)
       (sort-by current-nickname)))

(defn create-contacts-state [tuple-space own-puk puk->party]
  (let [nick->contact (atom {})
        puk->contact (atom {})
        contacts (restore-contacts tuple-space nick->contact puk->contact own-puk puk->party)]
    (doseq [^Contact c contacts]
      (when-let [party (.. c party current)]
        (when-let [old-contact (get @puk->contact (.. party publicKey current))]
          (swap! nick->contact dissoc (.. old-contact nickname current)))
        (swap! puk->contact assoc (.. party publicKey current) c))
      (swap! nick->contact assoc (.. c nickname current) c))

    {:own-puk             own-puk
     :tuple-space         tuple-space
     :nick->contact       nick->contact
     :puk->contact        puk->contact
     :observable-contacts (rx/map ->contact-list
                                  (atom->obs nick->contact))}))

(defn find-contact-in [puk->contact party]
  (get puk->contact (party->puk party)))

(defn find-contact [contacts-state party]
  (find-contact-in @(:puk->contact contacts-state) party))

(defn find-by-nick [contacts-state nick]
  (get @(:nick->contact contacts-state) nick))

(defn check-new-contact [nick->contact puk->contact nickname party]
  (when (some->> party (find-contact-in puk->contact))
    (throw (FriendlyException. "Duplicate contact")))
  (check-new-nickname nick->contact puk->contact nickname party))

(defn check-existing-contact [contacts-state party contact]
  (cond
    (= party (-> contact .party .current))
    contact

    (find-contact contacts-state party)
    (throw (FriendlyException. "Party already is a contact"))

    (nil? (-> contact .party .current))
    contact

    :else
    (throw (FriendlyException. "Nickname already used"))))

(defn- create-contact [contacts-state nickname party invite-code-received]
  (let [nick->contact-atom (:nick->contact contacts-state)
        puk->contact-atom (:puk->contact contacts-state)
        space (:tuple-space contacts-state)
        own-puk (:own-puk contacts-state)
        invite-code (or invite-code-received
                        (-> (UUID/randomUUID) .toString (.replaceAll "-" "")))]

    (check-new-contact @nick->contact-atom @puk->contact-atom nickname party)

    (publish-contact space own-puk nickname party invite-code)

    (when invite-code-received
      (.. ^TupleSpace space
          publisher
          (audience (-> party .publicKey .current))
          (type "push")
          (field "invite-code" invite-code-received)
          pub))

    (let [contact (reify-contact space nick->contact-atom puk->contact-atom own-puk nickname party invite-code)]
      (swap! nick->contact-atom assoc nickname contact)
      contact)))

(defn produce-contact [contacts-state nickname party invite-code-received]
  (assert (or (and (nil? party) (nil? invite-code-received))
              party))
  (let [contact (or (some->> nickname
                             (find-by-nick contacts-state)
                             (check-existing-contact contacts-state party))
                    (create-contact contacts-state nickname party invite-code-received))]
    (when party
      (swap! (:puk->contact contacts-state) assoc (party->puk party) contact)
      (when (not= party (-> contact .party .current))
        (.setParty contact party)))
    contact))

(defn get-contacts [contacts-state]
  (:observable-contacts contacts-state))

(defn puk->contact [contacts-state puk]
  (get @(:puk->contact contacts-state) puk))
