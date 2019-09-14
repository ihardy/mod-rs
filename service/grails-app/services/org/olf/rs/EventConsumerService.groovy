package org.olf.rs;

import static grails.async.Promises.*
import static groovy.json.JsonOutput.*

import org.apache.kafka.clients.consumer.KafkaConsumer

import grails.async.Promise
import grails.core.GrailsApplication
import grails.events.EventPublisher
import grails.events.annotation.Subscriber
import groovy.json.JsonSlurper

/**
 * Listen to configured KAFKA topics and react to them.
 * consume messages with topic TENANT_mod_rs_PatronRequestEvents for each TENANT activated - we do this as an explicit list rather
 * than as a regex subscription - so that we never consume a message for a tenant that we don't know about.
 * If the message parses, emit an asynchronous grails event.
 * This class is essentially the bridge between whatever event communication system we want to use and our internal method of
 * reacting to application events. Whatever implementation is used, it ultimately needs to call notify('PREventIndication',DATA) in order
 * for 
 */
public class EventConsumerService implements EventPublisher {

  GrailsApplication grailsApplication

  private KafkaConsumer consumer = null;
  private boolean running = true;
  private boolean tenant_list_updated = false;
  private Set tenant_list = null;

  @javax.annotation.PostConstruct
  public void init() {
    log.debug("Configuring event consumer service")
    Properties props = new Properties()
    grailsApplication.config.events.consumer.toProperties().each { final String key, final String value ->
      // Directly access each entry to cause lookup from env
      String prop = grailsApplication.config.getProperty("events.consumer.${key}")
      props.setProperty(key, prop)
    }
    log.debug("Configure consumer ${props}")
    consumer = new KafkaConsumer(props)

    Promise p = task {
      consumePatronRequestEvents();
    }

    p.onError { Throwable err ->
      log.warn("Problem with consumer",e);
    }

    p.onComplete { result ->
      log.debug("Consumer exited cleanly");
    }
  }

  private void consumePatronRequestEvents() {

    try {
      while ( running ) {

        def topics = null;
        if ( ( tenant_list == null ) || ( tenant_list.size() == 0 ) )
          topics = ['dummy_topic']
        else
          topics = tenant_list.collect { "${it}_mod_rs_PatronRequestEvents".toString() }

        log.debug("Listening out for topics : ${topics}");
        tenant_list_updated = false;
        consumer.subscribe(topics)
        while ( ( tenant_list_updated == false ) && ( running == true ) ) {
          def consumerRecords = consumer.poll(1000)
          consumerRecords.each{ record ->
            try {
              log.debug("KAFKA_EVENT:: topic: ${record.topic()} Key: ${record.key()}, Partition:${record.partition()}, Offset: ${record.offset()}, Value: ${record.value()}");

              // Convert the JSON payload string to a map 
              def jsonSlurper = new JsonSlurper()
              def data = jsonSlurper.parseText(record.value)
              if ( data.event != null ) {
                notify('PREventIndication', data)
              }
              else {
                log.debug("No handlers registered for event ${data.event}");
              }
            }
            catch(Exception e) {
              log.error("problem processing event notification",e);
            }

          }
          consumer.commitAsync();
        }
      }
    }
    catch ( Exception e ) {
      // log.error("Problem in consumer",e);
    }
    finally {
      consumer.close()
    }
  }

  @javax.annotation.PreDestroy
  private void cleanUp() throws Exception {
    log.info("EventConsumerService::cleanUp");
    running = false;

    // @See https://stackoverflow.com/questions/46581674/how-to-finish-kafka-consumer-safetyis-there-meaning-to-call-threadjoin-inside
    consumer.wakeup();
  }

  @Subscriber('okapi:tenant_list_updated')
  public void onTenantListUpdated(event) {
    log.debug("onTenantListUpdated(${event}) data:${event.data} -- Class is ${event.class.name}");
    tenant_list = event.data
    tenant_list_updated = true;
  }
}