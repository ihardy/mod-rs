package org.olf.rs

import grails.testing.mixin.integration.Integration
import grails.transaction.*
import static grails.web.http.HttpHeaders.*
import static org.springframework.http.HttpStatus.*
import spock.lang.*
import geb.spock.*
import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import groovy.util.logging.Slf4j
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.k_int.okapi.OkapiHeaders
import spock.lang.Shared
import grails.gorm.multitenancy.Tenants

import grails.databinding.SimpleMapDataBindingSource
import grails.web.databinding.GrailsWebDataBinder
import org.olf.okapi.modules.directory.DirectoryEntry
import grails.gorm.multitenancy.Tenants
import javax.sql.DataSource
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.beans.factory.annotation.Value

@Slf4j
@Integration
@Stepwise
class RSLifecycleSpec extends GebSpec {

  @Shared
  private Map test_info = [:]

  // Auto injected by spring
  def grailsApplication
  EventPublicationService eventPublicationService
  GrailsWebDataBinder grailsWebDataBinder
  HibernateDatastore hibernateDatastore
  DataSource dataSource

  static Map request_data = [:];

  final Closure authHeaders = {
    header OkapiHeaders.TOKEN, 'dummy'
    header OkapiHeaders.USER_ID, 'dummy'
    header OkapiHeaders.PERMISSIONS, '[ "rs.admin", "rs.user", "rs.own.read", "rs.any.read"]'
  }

  final static Logger logger = LoggerFactory.getLogger(RSLifecycleSpec.class);

  def setup() {
  }

  def cleanup() {
  }

  void "Set up test tenants "(tenantid, name) {
    when:"We post a new tenant request to the OKAPI controller"
      Thread.sleep(2000);
      logger.debug("Post new tenant request for ${tenantid} to ${baseUrl}_/tenant");

      def json_payload='{"parameters":[{"key":"loadReference","value":true},{"key":"loadSample", "value":true}]}';

      def resp = restBuilder().post("${baseUrl}_/tenant") {
        // parameters:[[value:true, key:loadSample], [value:true, key:loadReference]], module_from:mod-rs-1.2.0])
        header 'X-Okapi-Tenant', tenantid
        authHeaders.rehydrate(delegate, owner, thisObject)()
        contentType 'application/json; charset=UTF-8'
        accept 'application/json; charset=UTF-8'
        json json_payload
      }

    then:"The response is correct"
      resp.status == CREATED.value()
      logger.debug("Post new tenant request for ${tenantid} to ${baseUrl}_/tenant completed");
      // The tenant initiation step can take a few moments to complete... So wait.
      // Ideally this will be replaced with a DB op to check the DB changelog and look for completion
      Thread.sleep(5000);
      logger.debug("Done waiting for tenant initiation.. continue");

    where:
      tenantid | name
      'TestTenantG' | 'TestTenantG'
      'TestTenantH' | 'TestTenantH'
  }

  void "Test eventing"(tenant_id, entry_id, entry_uri) {
    when:"We emit a kafka event"
    logger.debug("Publish ${entry_uri}");
    eventPublicationService.publishAsJSON('modDirectory-entryChange-'+tenant_id,
        java.util.UUID.randomUUID().toString(),
        [ 'test': 'test' ] )

    then:"The response is correct"

    where:
    tenant_id | entry_id | entry_uri
    'TestTenantG' | 'TNS' | 'https://raw.githubusercontent.com/openlibraryenvironment/mod-directory/master/seed_data/TheNewSchool.json'
    'TestTenantG' | 'AC' | 'https://raw.githubusercontent.com/openlibraryenvironment/mod-directory/master/seed_data/AlleghenyCollege.json'
    'TestTenantG' | 'DIKU' | 'https://raw.githubusercontent.com/openlibraryenvironment/mod-directory/master/seed_data/DIKU.json'
  }


  /**
   * Pay CAREFUL attention here - the AVL symbol we are setting up loops back around to the /rs/iso18626 endpoint
   * running on the same test instance. This is not how real life works. All nodes in the network need the same directory information.
   * normally this comes from the network, but this function seeds that manually instead. N.B. the same data being loaded into 2 different
   * tenants
   */
  void "Bootstrap directory data for integration tests"(String tenant_id, Map entry) {
    when:"Load the default directory (test url is ${baseUrl})"

    Tenants.withId(tenant_id.toLowerCase()+'_mod_rs') {
      logger.debug("Sync directory entry ${entry}")
      def SimpleMapDataBindingSource source = new SimpleMapDataBindingSource(entry)
      DirectoryEntry de = new DirectoryEntry()
      grailsWebDataBinder.bind(de, source)

      logger.debug("Before save, ${de}, services:${de.services}");
      de.save(flush:true, failOnError:true)
      logger.debug("Result of bind: ${de} ${de.id}");
    }

    then:"Test directory entries are present"
    1==1

    where:
    tenant_id | entry
    'TestTenantH' | [ id:'RS-T-D-0001', name: 'Allegheny College', slug:'Allegheny_College',
      symbols: [
        [ authority:'OCLC', symbol:'AVL', priority:'a'] 
      ],
      services:[
        [
          slug:'Allegheny_College_ISO18626',
          service:[ 'name':'ReShare ISO18626 Service', 'address':"${baseUrl}/rs/externalApi/iso18626", 'type':'ISO18626', 'businessFunction':'ILL' ],
          customProperties:[ 'ILLPreferredNamespaces':['RESHARE', 'PALCI', 'IDS'] ]
        ]
      ]
    ]
    'TestTenantH' | [ id:'RS-T-D-0002', name: 'The New School', slug:'THE_NEW_SCHOOL', symbols: [[ authority:'OCLC', symbol:'PPPA', priority:'a'] ]]
    'TestTenantG' | [ id:'RS-T-D-0001', name: 'Allegheny College', slug:'Allegheny_College', symbols: [[ authority:'OCLC', symbol:'AVL', priority:'a'] ], 
                      services:[ [ slug:'Allegheny_College_ISO18626', 
                                   service:[ 'name':'ReShare ISO18626 Service', 'address':"${baseUrl}/rs/externalApi/iso18626", 'type':'ISO18626', 'businessFunction':'ILL' ], 
                      customProperties:[ 'ILLPreferredNamespaces':['RESHARE', 'PALCI', 'IDS'] ] ] ] ]
    'TestTenantG' | [ id:'RS-T-D-0002', name: 'The New School', slug:'THE_NEW_SCHOOL', symbols: [[ authority:'OCLC', symbol:'PPPA', priority:'a'] ]]
  }

  void "test settings interface - set symbol RESHARE:wibble for tenant TestTenantH"() {
    when:
      String json_payload = '{ "symbol": "RESHARE:wibble" }'

      RestResponse post_resp = restBuilder().post("${baseUrl}/rs/settings/tenantSymbols") {
        header 'X-Okapi-Tenant', 'TestTenantH'
        contentType 'application/json; charset=UTF-8'
        accept 'application/json; charset=UTF-8'
        authHeaders.rehydrate(delegate, owner, thisObject)()
        json json_payload
      }
      logger.debug("post response: ${post_resp}");

    then:
      RestResponse get_resp = restBuilder().get("${baseUrl}/rs/settings/tenantSymbols") {
        header 'X-Okapi-Tenant', 'TestTenantH'
        contentType 'application/json; charset=UTF-8'
        accept 'application/json; charset=UTF-8'
        authHeaders.rehydrate(delegate, owner, thisObject)()
      }
      logger.debug("get response as json: ${get_resp.json}");

      // Assert that the list of symbols for TestTenantH that we get back includes the newly registered one
      assert get_resp.json.symbols.contains('RESHARE:WIBBLE');
  }

  void "Create a new request with a ROTA pointing to Allegheny College"(tenant_id, p_title, p_author, p_systemInstanceIdentifier, p_patron_id, p_patron_reference) {
    when:"post new request"
    logger.debug("Create a new request ${tenant_id} ${p_title} ${p_patron_id}");

    // Create a request from OCLC:PPPA TO OCLC:AVL
    def req_json_data = [
      requestingInstitutionSymbol:'OCLC:PPPA',
      title: p_title,
      author: p_author,
      systemInstanceIdentifier: p_systemInstanceIdentifier,
      patronReference:p_patron_reference,
      patronIdentifier:p_patron_id,
      isRequester:true,
      rota:[
        [directoryId:'OCLC:AVL', rotaPosition:"0", 'instanceIdentifier': '001TagFromMarc', 'copyIdentifier':'COPYBarcode from 9xx']
      ],
      tags: [ 'RS-TESTCASE-1' ]
    ]

    String json_payload = new groovy.json.JsonBuilder(req_json_data).toString()

    RestResponse resp = restBuilder().post("${baseUrl}/rs/patronrequests") {
      header 'X-Okapi-Tenant', tenant_id
      contentType 'application/json; charset=UTF-8'
      accept 'application/json; charset=UTF-8'
      authHeaders.rehydrate(delegate, owner, thisObject)()
      json json_payload
    }
    logger.debug("CreateReqTest1 -- Response: RESP:${resp} JSON:${resp.json.id}");

    // Stash the ID
    this.request_data['RS-LIFECYCLE-TEST-00001'] = resp.json.id
    logger.debug("Created new request for with-rota test case 1. ID is : ${request_data['RS-LIFECYCLE-TEST-00001']}")


    then:"Check the return value"
    resp.status == CREATED.value()
    assert request_data['RS-LIFECYCLE-TEST-00001'] != null;

    where:
    tenant_id | p_title | p_author | p_systemInstanceIdentifier | p_patron_id | p_patron_reference
    'TestTenantG' | 'Brain of the firm' | 'Beer, Stafford' | '1234-5678-9123-4566' | '1234-5678' | 'RS-LIFECYCLE-TEST-00001'
  }

  /**
   *  Make sure that a reciprocal request has been created in TestTenantH
   */
  void "Ensure TestTenantH (OCLC:AVL) now contains a request with patronReference 'RS-LIFECYCLE-TEST-00001'"() {

    def pr = null;

    when:
      Tenants.withId('testtenanth_mod_rs') {
        waitFor(8, 1) {
          PatronRequest.withNewTransaction {
            logger.debug("Current requests for PatronRequest in testtenanth");
            logger.debug("${PatronRequest.list()}");
            pr = PatronRequest.findByPatronReference('RS-LIFECYCLE-TEST-00001')
          }

          pr != null
        }
      }
      log.debug("Found patron request ${pr} in TestTenantH");

    then:
      assert pr != null;
  }
  
  void "Wait for the new request to have state REQ_REQUEST_SENT_TO_SUPPLIER"(tenant_id, ref) {

    boolean completed = false;
    String final_state = null;

    when:"post new request"

      Tenants.withId(tenant_id.toLowerCase()+'_mod_rs') {

        waitFor(10, 1) {
          PatronRequest.withNewTransaction {
            logger.debug("waiting for request id: ${request_data['RS-LIFECYCLE-TEST-00001']} to have state REQ_REQUEST_SENT_TO_SUPPLIER")
            def r = PatronRequest.executeQuery('select count(pr) from PatronRequest as pr where pr.id = :rid and pr.state.code = :rsts', 
                                                [rid: this.request_data['RS-LIFECYCLE-TEST-00001'], rsts: 'REQ_REQUEST_SENT_TO_SUPPLIER'])[0];
  
            if(r == 1) {
              final_state = 'REQ_REQUEST_SENT_TO_SUPPLIER'
            }
            else {
              logger.debug("request id: ${request_data['RS-LIFECYCLE-TEST-00001']} - waiting for final state REQ_REQUEST_SENT_TO_SUPPLIER. ${r} matches");
            }
          }
          final_state == 'REQ_REQUEST_SENT_TO_SUPPLIER'
        }
      }

    then:"Check the return value"
      assert final_state == 'REQ_REQUEST_SENT_TO_SUPPLIER'

    where:
      tenant_id|ref
      'TestTenantG' | 'RS-T-D-0001'
  }

  void "Delete the tenants"(tenant_id, note) {

    expect:"post delete request to the OKAPI controller for "+tenant_id+" results in OK and deleted tennant"
      // Snooze
      try {
        Thread.sleep(2000);
      }
      catch ( Exception e ) {
        e.printStackTrace()
      }

      def resp = restBuilder().delete("$baseUrl/_/tenant") {
        header 'X-Okapi-Tenant', tenant_id
        authHeaders.rehydrate(delegate, owner, thisObject)()
      }

      logger.debug("completed DELETE request on ${tenant_id}");
      resp.status == NO_CONTENT.value()
      Thread.sleep(2000);

    where:
      tenant_id | note
      'TestTenantG' | 'note'
      'TestTenantH' | 'note'
  }

  RestBuilder restBuilder() {
    new RestBuilder()
  }

}

