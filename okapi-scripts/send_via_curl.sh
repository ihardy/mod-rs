
tgq=$(uuidgen)
tq=$(uuidgen)

payload='{
  "properties":{},
  "routing_key":"RSOutViaProtocol.TCP",
  "payload_encoding":"string",
  "payload":"{
    \"header\":{
      \"protocol\":\"TCP\",
      \"address\":\"localhost\",
      \"port\":8999
    },
    \"message\":{
      \"request\": {
        \"protocol_version_num\":1,
        \"transaction_id\":{
          \"transaction_group_qualifier\":\"'$tgq'\",
          \"transaction_qualifier\":\"'$tq'\"
        },
        \"service_date_time\": {
          \"date_time_of_this_service\":{\"date\":\"20170101\", \"time\":\"0000\"},
          \"date_time_of_original_service\":{\"date\":\"20180101\",\"time\":\"1111\"},
        },
        \"requester_id\":{
          \"person_or_institution_symbol\":{
            \"institution_symbol\":\"RESHARE:ACMAIN\"
          }
        },
        \"responder_id\":{
          \"person_or_institution_symbol\":{
            \"institution_symbol\":\"RESHARE:DIKUA\"
          }
        },
        \"transaction_type\":\"simple\",
        \"iLL_service_type\":[\"loan\",\"copy-non-returnable\",\"locations\",\"estimate\",\"responder-specific\"],
        \"requester_optional_messages\":{
                  \"can_send_RECEIVED\":true,
                  \"can_send_RETURNED\":true,
                  \"requester_SHIPPED\":\"desires\",
                  \"requester_CHECKED_IN\":\"desires\"
        },
        \"place_on_hold\": \"according_to_policy\",
        \"client_id\":{
          \"client_name\":\"Ian Ibbotson\",
          \"client_status\":\"Current\",
          \"client_identifier\":\"ianibbo\"
        },
        \"item_id\":{
          \"title\":\"The Shadow Saga\",
          \"author\":\"Orson Scott Card\",
          \"publisher\":\"A test publisher\",
          \"sub_title\":\"A test subtitle\",
          \"title_of_article\":\"A test title_of_article\",
          \"iSBN\":\"A test ISBN\",
          \"iSSN\":\"A test ISSN\",
          \"pagination\":\"A test pagination\"
        },
        \"retry_flag\":false,
        \"forward_flag\":false,
        \"requester_note\":\"ILLTEST-CASE-001\"
      }
    }
  }"
}'

curl -u rsms:rsms -H "content-type:application/json" -X POST -d"$payload" http://localhost:15672/api/exchanges/%2f/RSExchange/publish
