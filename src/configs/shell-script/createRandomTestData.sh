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


# A bash shell script to generate random test data that can be loaded into Kafka topics
# The data will be output to a text file specified by a parameter passed to the script

#Usage: createRandomTestData.sh FILENAME COUNTRY_CODES COMPANY_NAMES SURNAMES FIRST_NAMES NUMBER_OF_RECORDS DATA_DELIMITER
#FILENAME - the name of the file to write the random test data to
#COUNTRY_CODES - a space separated list of country codes to use in the random test data
#COMPANY_NAMES - a space separated list of company names to use in the random test data
#SURNAMES - a space separated list of surnames to use in the random test data
#FIRST_NAMES - a space separated list of first names to use in the random test data
#NUMBER_OF_RECORDS - the number of random test data records to generate
#DATA_DELIMITER - the delimiter to use between the fields in the random test data records (optional - default is "¬")

#Example: ./createRandomTestData.sh random-test-data-large.dat "GBR IRL FRA" "company1 company2 company3" "surname1 surname2 surname3" "firstname1 firstname2 firstname3" 100 "¬"

#The script generates random test data records in the following format:
#Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples <http://www.<COMPANY_NAME>.io/fake_data#person<ID>> <http://<COMPANY_NAME>/ontology/primaryName> "<FIRST_NAME>, <SURNAME>" . <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#Person> .
#Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples <http://www.<COMPANY_NAME>.io/fake_data#person<ID>> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://ies.data.gov.uk/ontology/ies4#Person> .
#Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples <http://www.<COMPANY_NAME>.io/fake_data#person<ID>> <http://ies.data.gov.uk/ontology/ies4#owns> <http://www.<COMPANY_NAME>.io/fake_data#car<ID>> <http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy> <http://www.<COMPANY_NAME>.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197> .


#Turn on debugging
#set -x

TAB_CHAR=$'\t'

#Set the locale to ensure the script runs in a consistent locale environment
#Needed to run the script within MacOs
export LC_CTYPE=C
export LANG=C

#Utility random data generators for the test data - country codes and company names
#function to generate a random country code given an array of country codes
function generateRandomCountryCode() {
  countryCodesArray=$1
  randomIndex=$((RANDOM % ${#countryCodesArray[@]}))
  randomCountryCode=${countryCodesArray[$randomIndex]}
  echo "${randomCountryCode}"
}

#function to generate a random company name given an array of company names
function generateRandomCompanyName() {
  companyNamesArray=$1
  randomIndex=$((RANDOM % ${#companyNamesArray[@]}))
  randomCompanyName=${companyNamesArray[$randomIndex]}
  echo "${randomCompanyName}"
}

#function to generate a random surname given an array of surnames
function generateRandomSurname() {
  surnamesArray=$1
  randomIndex=$((RANDOM % ${#surnamesArray[@]}))
  randomSurname=${surnamesArray[$randomIndex]}
  echo "${randomSurname}"
}

#function to generate a random first name given an array of first names
function generateRandomFirstName() {
  firstNamesArray=$1
  randomIndex=$((RANDOM % ${#firstNamesArray[@]}))
  randomFirstName=${firstNamesArray[$randomIndex]}
  echo "${randomFirstName}"
}

# Security-Label
#    Example: Security-Label:nationality=GBR¬Content-Type:application/n-triples
#    Format: Security-Label:nationality=<COUNTRY_CODE>¬Content-Type:application/n-triples
#function to generate the first field the Security Label given a country code
function generateSecurityLabel() {
  countryCode=$1
  securityLabel="Security-Label:nationality=${countryCode}${DATA_DELIMITER}Content-Type:application/n-triples${TAB_CHAR}"
  echo "${securityLabel}"
}

# Person url
#    Example: <http://iso.io/fake_data#person_00677_SURNAME>
#    Format: <http://www.<companyName>.io/fake_data#person<ID>_<SURNAME>
#function to generate the second field given a company name
function generateFakePersonUrl() {
  companyName=$1
  randomNumber=$(tr -dc '0-9' < /dev/urandom | fold -w 6 | head -n 1)
  fakePerson="<http://www.${companyName}.io/fake_data#person_${randomNumber}>"
  echo "${fakePerson}"
}

# Person url
#    Example: <http://iso.io/fake_data#person_00677_SURNAME>
#    Format: <http://www.<companyName>.io/fake_data#person<ID>_<SURNAME>
#function to generate the second field given a company name
function generateFakePersonSurnameUrl() {
  companyName=$1
  randomSurname=$(generateRandomSurname "${surnamesArray[@]}")
  randomNumber=$(tr -dc '0-9' < /dev/urandom  | fold -w 6 | head -n 1)
  personUrl="<http://www.${companyName}.io/fake_data#person_${randomNumber}_${randomSurname}>"
  echo "${personUrl}"
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
  echo "${primaryName}"
}

# Car url
#    Example: <http://iso.io/fake_data#car_00677>
#    Format: <http://www.<companyName>.io/fake_data#car<ID>

#function to generate the second field car url given a company name
function generateFakeCarUrl() {
  companyName=$1
  randomNumber=$(tr -dc '0-9' < /dev/urandom | fold -w 6 | head -n 1)
  fakeCar="<http://www.${companyName}.io/fake_data#car_${randomNumber}>"
  echo "${fakeCar}"
}

#Fake Data
# Example: <http://fakeCompany.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197>
# Format: <http://fakeCompany.io/fake_data#ef551229-1628-41e6-a5f6-78a2e9ca2197>
#function to generate fake data
function generateFakeData() {
  companyName=$1
  randomData=$(tr -dc 'a-z0-9' < /dev/urandom | fold -w 36 | head -n 1)
  fakeData="<http://${companyName}/fake_data#${randomData}>"
  echo "${fakeData}"
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
  echo ${iesPerson}
}

# <http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy>
function generateIesIsIdentifiedBy() {
  iesIsIdentifiedBy="<http://ies.data.gov.uk/ontology/ies4#isIdentifiedBy>"
  echo ${iesIsIdentifiedBy}
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
  toReturn="${securityLabel}${fakePerson} ${primaryName} ${fakePerson} ${rdfSyntaxNsType} ${iesPerson} ."
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
  toReturn="${securityLabel}${fakeSurname} ${rdfSyntaxNsType} ${iesPerson} ."
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
NOW=$(date +"%Y-%m-%d-%H-%M-%S")

#Input parameters
FILENAME=$1
COUNTRY_CODES=$2
COMPANY_NAMES=$3
SURNAMES=$4
FIRST_NAMES=$5
NUMBER_OF_RECORDS=$6
DATA_DELIMITER=$7

#Check for any missing input parameters
if [ -z "${FILENAME}" ] || [ -z "${COUNTRY_CODES}" ] || [ -z "${COMPANY_NAMES}" ] || [ -z "${SURNAMES}" ] || [ -z "${FIRST_NAMES}" ] || [ -z "${NUMBER_OF_RECORDS}" ]; then
  echo "--> ERROR: Missing input parameters. Please provide the following parameters in order: FILENAME COUNTRY_CODES COMPANY_NAMES SURNAMES FIRST_NAMES NUMBER_OF_RECORDS"
  exit 1
fi

#Check if DATA_DELIMITER is empty and if so set it to a default value
if [ -z "${DATA_DELIMITER}" ]; then
  DATA_DELIMITER="¬"
fi

echo "--> Start of ${0} running on machine ${HOSTNAME} with the following input parameters:"
echo "---> FILENAME: ${FILENAME}"
echo "---> COUNTRY_CODES: ${COUNTRY_CODES}"
echo "---> COMPANY_NAMES: ${COMPANY_NAMES}"
echo "---> SURNAMES: ${SURNAMES}"
echo "---> FIRST_NAMES: ${FIRST_NAMES}"
echo "---> NUMBER_OF_RECORDS: ${NUMBER_OF_RECORDS}"
echo "---> DATA_DELIMITER: ${DATA_DELIMITER}"

#Extract the parameters strings from each environment variables and store in arrays
IFS=' ' read -r -a countryCodesArray <<< "$COUNTRY_CODES"
IFS=' ' read -r -a companyNamesArray <<< "$COMPANY_NAMES"
IFS=' ' read -r -a surnamesArray <<< "$SURNAMES"
IFS=' ' read -r -a firstNamesArray <<< "$FIRST_NAMES"

#Debug - echo out the contents of the arrays
#echo "--> countryCodesArray: ${countryCodesArray[*]}"
#echo "--> companyNamesArray: ${companyNamesArray[*]}"
#echo "--> surnamesArray: ${surnamesArray[*]}"
#echo "--> firstNamesArray: ${firstNamesArray[*]}"

#Check if the file exists and if so rename it to a date stamped backup file
if [ -f "${FILENAME}" ]; then
  mv "${FILENAME}" "${FILENAME}"-"${NOW}".bak
fi

echo
echo "--> Writing random test data to file ${FILENAME} to create ${NUMBER_OF_RECORDS} records (please wait)"

#Generate the random test data and write to the file
for ((i=0; i<"$NUMBER_OF_RECORDS"; i++))
do
  randomData=$(generateFakeDataToSendToTopic)
  echo "${randomData}" >> "${FILENAME}"
done

echo "--> Wrote random test data to file ${FILENAME} with ${NUMBER_OF_RECORDS} records"
echo "--> End of ${0} running on machine ${HOSTNAME}"
