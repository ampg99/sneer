(ns sneer.impl
  (:require
   [sneer.conversations :refer [reify-conversations]]
   [sneer.contact :refer [create-contacts-state produce-contact get-contacts find-contact find-by-nick problem-with-new-nickname]]
   [sneer.party :refer [party->puk reify-party produce-party! create-puk->party]])
  (:import
    [sneer Sneer PrivateKey]
    [sneer.tuples TupleSpace]))

(defn new-sneer [^TupleSpace tuple-space ^PrivateKey own-prik]
  (let [own-puk (.publicKey own-prik)
        puk->party (create-puk->party)
        contacts-state (create-contacts-state tuple-space own-puk puk->party)
        contacts (get-contacts contacts-state)
        conversations (reify-conversations own-puk tuple-space contacts-state)
        self (reify-party own-puk)]

    (reify Sneer
      (self [_] self)

      (contacts [_]
        contacts)

      (problemWithNewNickname [_ new-nick party]
        (problem-with-new-nickname contacts-state new-nick party))

      (produceContact [_ nickname party invite-code-received]
        (produce-contact contacts-state nickname party invite-code-received))

      (findContact [_ party]
        (find-contact contacts-state party))
      (findByNick [_ nick]
        (find-by-nick contacts-state nick))

      (produceParty [_ puk]
        (produce-party! puk->party puk))

      (tupleSpace [_]
        tuple-space)

      (conversations [_]
        conversations))))
