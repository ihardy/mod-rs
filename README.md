mod-rs - Providing Resource Sharing Capabilities

# I'm a front end developer - what resources are available

mod-rs is not a document-storage oriented RMB module, it is a hibernate / ORM based object/relational app. Although this arrangement
requires more effort to add properties (Note though that all the primary domain objects support an extensible document-like customProperties structure)
it has the advantage of being dynamically tuned and queryable in response to changing and evolving domain model requirements in a way that document stores often 
are not, particularly when dealing with highly relational data. This is expecially true when filtering on joined collections.

Because of this, RAML and Json Schema are not used to define out private internal storage model - they arise out of it. This means mod-rs
interface can often hide information from clients and provide a sensible boundary between callers and business/storage logic.

Since our interface arises out of the domain model, rather than having the external interface dictate the internal storage model the RAML and JsonSchema
descriptions of this module are made available through the API itself at the following endpoints

| Artefact | Call URL | Notes |
|---|---|---|
| RAML Description | /rs/kiwt/raml | human maintained in service/grails-app/controllers/mod/rs/RSConfigurationController |
| All Schema Objects | /rs/kiwt/config/schema | All schema objects |
| Stand Alone Schema - Patron request | /rs/kiwt/config/schema/PatronRequest | The schema for PatronRequest with all it's sub-objects embedded in a single schema |
| Embedded Schema | /rs/kiwt/config/schema/embedded/PatronRequest | The schema for PatronRequest with all it's sub-objects embedded in a single schema |

All the basic object types can be listed as stand-alone objects or as embedded structures

# I'm a backend developer, where should I start

## Probably with the domain model

The [domain model](https://github.com/openlibraryenvironment/mod-rs/service/grails-app/domain/org/olf/rs) is the heart of the module - you should probably
start there. The domain model also imports the shared [directory model](https://github.com/openlibraryenvironment/dm-directory) and a number of classes 
for refdata, custom properties, tags and other common structures from this [utility library](https://github.com/k-int/web-toolkit-ce).

## And the event model

mod-rs is slightly unusual in that it often mediates messages passed between tenants in an okapi environment. These events are passed over a kafka substrate
and the majority of the work is done in [services]((https://github.com/openlibraryenvironment/mod-rs/service/grails-app/services). In particular, the 
service ReshareApplicationEventHandlerService.groovy is responsible for handling application level events. This service will receive an "STATUS_XXX_ind" whenever 
the state of a patron request changes - for example "STATUS_VALIDATED_ind" once a request status has been changed to VALIDATED. The service can also receive
other indications, for example "NewPatronRequest_ind" is called when a new patron request (Requester side) is created, and a "MESSAGE_REQUEST_ind" event
is raised on the lender side when a new request enters the system.

Please note well that in a scaled-out system, different instances of the running system will be removing notifications from the KAFKA queue - this
balances the load of processing events, indications and incoming protocol messages over the set of running modules.

The current events and the handlers that get called are (N.B. this is for illustrative purposes only, please see the code, and maybe update this
section if you notice discrepancies) 

| Event | Description | Handler |
|---|---|---|
|NewPatronRequest_ind|A new request (On the borrower side) was created|handleNewPatronRequestIndication|
|STATUS_VALIDATED_ind|A request went from IDLE -> VALIDATED|sourcePatronRequest|
|STATUS_SOURCING_ITEM_ind|A request went from VALIDATED -> SOURCING_ITEM||
|STATUS_SUPPLIER_IDENTIFIED_ind|A request went from SOURCING_ITEM -> SUPPLIER_IDENTIFIED |sendToNextLender|
|STATUS_REQUEST_SENT_TO_SUPPLIER_ind|A request went from SUPPLIER_IDENTIFIED -> REQUEST_SENT_TO_SUPPLIER ||
|STATUS_ITEM_SHIPPED_ind|A request moved to the SHIPPED state||
|STATUS_BORROWING_LIBRARY_RECEIVED_ind|||
|STATUS_AWAITING_RETURN_SHIPPING_ind|||
|STATUS_BORROWER_RETURNED_ind|||
|MESSAGE_REQUEST_ind|A Lender received a new request (Usually via an interlending protocol) for an item|handleRequestMessage|



## Vagrant container with Kafka 

This module is developed and tested in a vagrant container projectreshare/development - see
https://app.vagrantup.com/projectreshare/boxes/development.

to update this box, make whatever changes are needed and then

Remember that when starting a vagrant image, the directory containing the Vagrantfile will be mounted as /vagrant in the started image
This will give you an easy way to make an updated .deb avaialable, for example.

As root, run 

    ./vagrant-tidy.sh

Exit from the VM and run

    vagrant package

This will create package.box - Go to https://app.vagrantup.com/projectreshare/boxes/development and create a new version with provider virtualbox then
upload the new .box image.

## Domain Classes and Database Schemas

Schemas are controlled by the liquibase database migrations plugin. This means domain classes work sligthly differently to normal grails projects.

After adding or editing domain classes, you will need to generate a liquibase config file. The full file can be regenerated with::

    grails dbm-gorm-diff description-of-change.groovy --add
    grails dbm-generate-gorm-changelog my-new-changelog.groovy

_NOTE:_ If you are using the database from the vagrant image, which is on 54321 to avoid clashes with any local postgres you might have,
the above won't be able to find your database. Try:

    grails -Dgrails.env=vagrant-db dbm-gorm-diff description-of-change.groovy --add
    grails -Dgrails.env=vagrant-db dbm-generate-gorm-changelog my-new-changelog.groovy


