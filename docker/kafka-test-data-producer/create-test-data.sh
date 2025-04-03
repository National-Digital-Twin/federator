#!/bin/bash

# SPDX-License-Identifier: Apache-2.0
# © Crown Copyright 2025. This work has been developed by the National Digital Twin Programme
# and is legally attributed to the Department for Business and Trade (UK) as the governing entity.

# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Script to create test data and add it to kafka topics

# Parameters:
#   - KAFKA_BROKER_SERVERS: Kafka broker servers to connect to (space separated) - e.g. "kafka1:9092 kafka2:9092"
#   - KAFKA_TOPICS: Kafka topics to create (space separated) - e.g. "topic1 topic2"
#   - COUNTRY_CODES: Country codes to use in the test data (space separated) - e.g. "GB US FR"
#   - COMPANY_NAMES: Company names to use in the test data (space separated) - e.g. "kainos ibm microsoft"
#   - SURNAMES: Surnames to use in the test data (space separated) - e.g. "Smith Jones"
#   - FIRST_NAMES: First names to use in the test data (space separated) - e.g. "John Jane"
#   - DELAY_BETWEEN_DATA_LOADS: Delay between each time the data is sent to the kafka brokers/topics
#   - NUMBER_OF_DATA_LOADS: Total number of times to send data to kafka brokers/topics
#   - NUMBER_OF_MESSAGES_PER_DATA_LOAD: Total number of messages to send to each kafka broker/topic in each data load iteration
#   - DATA_DELIMITER: Delimiter to use when sending data to kafka topics

#The script generates random test data records in the following format:
#Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples <http://www.<COMPANY_NAME>.io/fake_data#person<ID>> <http://<COMPANY_NAME>/ontology/primaryName> "<FIRST_NAME>, <SURNAME>" . <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#Person> .
#Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples <http://www.<COMPANY_NAME>.io/fake_data#person<ID>> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#Person> .
#Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples <http://www.<COMPANY_NAME>.io/fake_data#person<ID>> <http://ies.data.gov.uk/ontology/ies4#owns> <http://www.<COMPANY_NAME>.io/fake_data#car<ID>> <http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy> <http://www.<COMPANY_NAME>.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197> .


#Turn on debugging
#set -x

TAB_CHAR=$'\t'

#Utility random data generators for the test data - country codes and company names
#function to generate a random country code given an array of country codes
function generateRandomCountryCode() {
  countryCodesArray=$1
  randomIndex=$((RANDOM % ${#countryCodesArray[@]}))
  randomCountryCode=${countryCodesArray[$randomIndex]}
  echo "$randomCountryCode"
}

#function to generate a random company name given an array of company names
function generateRandomCompanyName() {
  companyNamesArray=$1
  randomIndex=$((RANDOM % ${#companyNamesArray[@]}))
  randomCompanyName=${companyNamesArray[$randomIndex]}
  echo "$randomCompanyName"
}

#function to generate a random surname given an array of surnames
function generateRandomSurname() {
  surnamesArray=$1
  randomIndex=$((RANDOM % ${#surnamesArray[@]}))
  randomSurname=${surnamesArray[$randomIndex]}
  echo "$randomSurname"
}

#function to generate a random first name given an array of first names
function generateRandomFirstName() {
  firstNamesArray=$1
  randomIndex=$((RANDOM % ${#firstNamesArray[@]}))
  randomFirstName=${firstNamesArray[$randomIndex]}
  echo "$randomFirstName"
}

# Security-Label
#    Example: Security-Label:nationality=GBR¬Content-Type:application/n-triples
#    Format: Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples
#function to generate the first field the Security Label given a country code
function generateSecurityLabel() {
  countryCode=$1
  securityLabel="Security-Label:nationality=${countryCode}${DATA_DELIMITER}Content-Type:application/n-triples${TAB_CHAR}"
  echo "$securityLabel"
}

# Person url
#    Example: <http://iso.io/fake_data#person_00677_SURNAME>
#    Format: <http://www.<companyName>.io/fake_data#person<ID>_<SURNAME>
#function to generate the second field given a company name
function generateFakePersonUrl() {
  companyName=$1
  randomNumber=$(tr -dc '0-9' < /dev/urandom | fold -w 6 | head -n 1)
  fakePerson="<http://www.${companyName}.io/fake_data#person_${randomNumber}>"
  echo "$fakePerson"
}

# Person url
#    Example: <http://iso.io/fake_data#person_00677_SURNAME>
#    Format: <http://www.<companyName>.io/fake_data#person<ID>_<SURNAME>
#function to generate the second field given a company name
function generateFakePersonSurnameUrl() {
  companyName=$1
  randomSurname=$(generateRandomSurname "${surnamesArray[@]}")
  randomNumber=$(tr -dc '0-9' < /dev/urandom | fold -w 6 | head -n 1)
  personUrl="<http://www.${companyName}.io/fake_data#person_${randomNumber}_${randomSurname}>"
  echo "$personUrl"
}

# Person Primary Name field
#  Example: <http://fake_company.io/ontology/primaryName> "Firstname, Surname" .
#  Format: <http://fake_company.io/ontology/primaryName> "Firstname, Surname" .
#function to generate the second field primary name given a company name
function generatePrimaryName() {
  companyName=$1
  randomSurname=$(generateRandomSurname "${surnamesArray[@]}")
  randomFirstName=$(generateRandomFirstName "${firstNamesArray[@]}")
  primaryName="<http://${companyName}/ontology/primaryName> \"${randomFirstName}, ${randomSurname}\" ."
  echo "$primaryName"
}

# Car url
#    Example: <http://iso.io/fake_data#car_00677>
#    Format: <http://www.<companyName>.io/fake_data#car<ID>

#function to generate the second field car url given a company name
function generateFakeCarUrl() {
  companyName=$1
  randomNumber=$(tr -dc '0-9' < /dev/urandom | fold -w 6 | head -n 1)
  fakeCar="<http://www.${companyName}.io/fake_data#car_${randomNumber}>"
  echo "$fakeCar"
}

#Fake Data
# Example: <http://fakeCompany.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197>
# Format: <http://fakeCompany.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197>
#function to generate fake data
function generateFakeData() {
  companyName=$1
  randomData=$(tr -dc 'a-z0-9' < /dev/urandom  | fold -w 36 | head -n 1)
  fakeData="<http://${companyName}/fake_data#${randomData}>"
  echo "$fakeData"
}

# rdf-syntax-ns#type
# example: <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>
# Format: <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> (no change)
#function to generate the rdf-syntax-ns#type tag
function generateRdfSyntaxNsType() {
  rdfSyntaxNsType="<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>"
  echo "${rdfSyntaxNsType}"
}

#<http://ies.data.gov.uk/ontology/ies4#Person>
function generateIesPerson() {
  iesPerson="<http://ies.data.gov.uk/ontology/ies4#Person>"
  echo "${iesPerson}"
}

# <http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy>
function generateIesIsIdentifiedBy() {
  iesIsIdentifiedBy="<http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy>"
  echo "${iesIsIdentifiedBy}"
}

#Fake Person
 ## Security-Label:nationality=IRL¬Content-Type:application/n-triples
 ## <http://fake_company.io/fake_data#person_00676>
 ##     <http://fake_company.io/ontology/primaryName> "Firstname, Surname" .
 ## <http://fake_company.io/fake_data#person_00676>
 ## <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>
 ## <http://ies.data.gov.uk/ontology/ies4#Person> .
function generateFakePerson() {
  randomCountryCode=$(generateRandomCountryCode "${countryCodesArray[@]}")
  randomCompanyName=$(generateRandomCompanyName "${companyNamesArray[@]}")
  securityLabel=$(generateSecurityLabel "${randomCountryCode}")
  fakePerson=$(generateFakePersonUrl "${randomCompanyName}")
  primaryName=$(generatePrimaryName "${randomCompanyName}")
  rdfSyntaxNsType=$(generateRdfSyntaxNsType)
  iesPerson=$(generateIesPerson)
  toReturn="${securityLabel} ${fakePerson} ${primaryName} ${fakePerson} ${rdfSyntaxNsType} ${iesPerson} ."
  echo "${toReturn}"
}

#Fake Person Surname
#Security-Label:nationality=IRL¬Content-Type:application/n-triples
# <http://fakeCompany.io/fake_data#person_00676_SURNAME>
# <http://www.w3.org/1999/02/22-rdf-syntax-ns#type>
# <http://ies.data.gov.uk/ontology/ies4#Surname> .
#function to generate a fake person surname given a company name
function generateFakePersonSurname() {
  randomCountryCode=$(generateRandomCountryCode "${countryCodesArray[@]}")
  randomCompanyName=$(generateRandomCompanyName "${companyNamesArray[@]}")
  securityLabel=$(generateSecurityLabel "${randomCountryCode}")
  fakeSurname=$(generateFakePersonSurnameUrl "${randomCompanyName}")
  rdfSyntaxNsType=$(generateRdfSyntaxNsType)
  iesPerson=$(generateIesPerson)
  toReturn="${securityLabel} ${fakeSurname} ${rdfSyntaxNsType} ${iesPerson} ."
  echo "${toReturn}"
}

#Fake Car
#Security-Label:nationality=GBR¬Content-Type:application/n-triples
# <http://fakeCompany.io/fake_data#car_00020>
#   <http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy>
# <http://fakeCompany.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197>
#function to generate a fake car
function generateFakeCar() {
  randomCountryCode=$(generateRandomCountryCode "${countryCodesArray[@]}")
  randomCompanyName=$(generateRandomCompanyName "${companyNamesArray[@]}")
  securityLabel=$(generateSecurityLabel "${randomCountryCode}")
  fakeCarId=$(generateFakeCarUrl "${randomCompanyName}")
  iesIsIdentifiedBy=$(generateIesIsIdentifiedBy)
  fakeCarData=$(generateFakeData "${randomCompanyName}")
  toReturn="${securityLabel}${fakeCarId} ${iesIsIdentifiedBy} ${fakeCarData} ."
  echo "${toReturn}"
}

#function to generate the data to send to the kafka topic randomly ether a person, person surname or car
function generateFakeDataToSendToTopic() {
  randomDataSelector=$((RANDOM % 3))
  case $randomDataSelector in
    0)
      fakeDataToSendToTopic=$(generateFakePerson)
      ;;
    1)
      fakeDataToSendToTopic=$(generateFakePersonSurname)
      ;;
    2)
      fakeDataToSendToTopic=$(generateFakeCar)
      ;;
  esac
  echo "${fakeDataToSendToTopic}"
}


#Script starts here
HOSTNAME=$(hostname)

echo "--> Start of ${0} running on machine ${HOSTNAME} with the following input parameters:"
echo "---> KAFKA_BROKER_SERVERS: ${KAFKA_BROKER_SERVERS}"
echo "---> KAFKA_TOPICS: ${KAFKA_TOPICS}"
echo "---> COUNTRY_CODES: ${COUNTRY_CODES}"
echo "---> COMPANY_NAMES: ${COMPANY_NAMES}"
echo "---> SURNAMES: ${SURNAMES}"
echo "---> FIRST_NAMES: ${FIRST_NAMES}"
echo "---> DELAY_BETWEEN_DATA_LOADS: ${DELAY_BETWEEN_DATA_LOADS}"
echo "---> NUMBER_OF_DATA_LOADS: ${NUMBER_OF_DATA_LOADS}"
echo "---> NUMBER_OF_MESSAGES_PER_DATA_LOAD: ${NUMBER_OF_MESSAGES_PER_DATA_LOAD}"
echo "---> DATA_DELIMITER: ${DATA_DELIMITER}"

if [ "${NUMBER_OF_DATA_LOADS}" -eq 0 ]; then
  echo "--> NUMBER_OF_DATA_LOADS is set to 0, exiting script"
  exit 0
fi

#Extract the parameters strings from each environment variables and store in arrays
IFS=' ' read -r -a kafkaBrokerServersArray <<< "$KAFKA_BROKER_SERVERS"
IFS=' ' read -r -a kafkaTopicsArray <<< "$KAFKA_TOPICS"
IFS=' ' read -r -a countryCodesArray <<< "$COUNTRY_CODES"
IFS=' ' read -r -a companyNamesArray <<< "$COMPANY_NAMES"
IFS=' ' read -r -a surnamesArray <<< "$SURNAMES"
IFS=' ' read -r -a firstNamesArray <<< "$FIRST_NAMES"

#Debug - echo out the contents of the arrays
echo "--> kafkaBrokerServersArray: ${kafkaBrokerServersArray[*]}"
echo "--> kafkaTopicsArray: ${kafkaTopicsArray[*]}"
echo "--> countryCodesArray: ${countryCodesArray[*]}"
echo "--> companyNamesArray: ${companyNamesArray[*]}"
echo "--> surnamesArray: ${surnamesArray[*]}"
echo "--> firstNamesArray: ${firstNamesArray[*]}"

#For each kafka broker server create the topics if they do not exist
for kafkaServer in "${kafkaBrokerServersArray[@]}"; do
  echo "--> Creating topics for kafka server - $kafkaServer"
  #For each topic check if it exists and if not create it
  for newTopic in "${kafkaTopicsArray[@]}"; do
    #Check if the topic exists
    kafka-topics --bootstrap-server "${kafkaServer}" --describe --topic "${newTopic}" 2>/dev/null
    topic_exists=$?
    if [ ${topic_exists} -ne 0 ]; then
      #Create the topic
      kafka-topics --create --topic "${newTopic}" --partitions 1 --replication-factor 1 --if-not-exists --bootstrap-server "${kafkaServer}"
      echo "---> Created topic - $newTopic"
    else
      echo "---> Topic already exists" - $newTopic
    fi
  done # end of for newTopic in "${kafkaTopicsArray[@]}"
done # end of for kafkaServer in "${kafkaBrokerServersArray[@]}"
echo "--> Finished creating topics"

#Load the data into the topics looped for the number of times specified in the NUMBER_OF_DATA_LOADS parameter
currentDataLoad=0
while [ $currentDataLoad -lt "$NUMBER_OF_DATA_LOADS" ]; do
  echo "--> Current data load: $currentDataLoad of a total of $NUMBER_OF_DATA_LOADS"
  #check for file and remove it if it exists
  [ -f /tmp/kafkaDataToSendToTopic.dat ] && rm /tmp/kafkaDataToSendToTopic.dat
  #Loop from 0 to NUMBER_OF_MESSAGES_PER_DATA_LOAD
  for i in $(seq 1 "$NUMBER_OF_MESSAGES_PER_DATA_LOAD"); do
    kafkaDataToSendToTopic=$(generateFakeDataToSendToTopic)
    echo "${kafkaDataToSendToTopic}" >> /tmp/kafkaDataToSendToTopic.dat
  done
  #For each kafka broker server send the data to the topics
  for kafkaServer in "${kafkaBrokerServersArray[@]}"; do
    echo "---> Sending ${NUMBER_OF_MESSAGES_PER_DATA_LOAD} messages to kafka server ${kafkaServer} for each topic ${kafkaTopicsArray[*]}"
    #For each topic send the data
    for newTopic in "${kafkaTopicsArray[@]}"; do
      #Send the data to the topic
      kafka-console-producer --bootstrap-server "${kafkaServer}" --topic "${newTopic}" --property parse.headers=true --property headers.separator="${DATA_DELIMITER}" < /tmp/kafkaDataToSendToTopic.dat
    done # end of for newTopic in "${kafkaTopicsArray[@]}"
  done # end of for kafkaServer in "${kafkaBrokerServersArray[@]}"
  ((currentDataLoad+=1))
  echo "---> Sleeping for ${DELAY_BETWEEN_DATA_LOADS}....."
  sleep "${DELAY_BETWEEN_DATA_LOADS}"
done

echo "--> Finished loading random data into kafka topics"
echo "--> End of ${0}"
exit 0

