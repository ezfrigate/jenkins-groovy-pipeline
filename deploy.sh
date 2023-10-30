#!/bin/sh

# Gather parameter from Jobs
PROJECT_NAME=${PROJECT_NAME}
INSTALLER_NAME=${INSTALLER_NAME}
ENV=${ENV}
VERSION=${VERSION}
INSTALLATION_MODE=${INSTALLATION_MODE}
MAVEN_REPOSITORY=${MAVEN_REPOSITORY}
LIST_SSH_SERVER=${LIST_SSH_SERVER}
IS_S3=${IS_S3}

# Build other ones
TGZ_NAME="${PROJECT_NAME}-${VERSION}.tar.gz"

# Url for Artifactory Server that host the maven repositories where to look in
ARTIFACTORY_DCES_BASE_URL=https://artifactory-dces.schneider-electric.com/artifactory
ARTIFACTORY_IPO_BASE_URL=http://10.155.34.21:8081/artifactory

# Location for Mvn local repository
MVN_REPO_BASEPATH="/var/lib/jenkins/.m2/repository/"

function error(){
  echo "########################################################################"
  echo "# Error : $*"
  echo "########################################################################"
  exit 1
}

# Delete every file in workspace to clean it up
rm -rf *

echo "===================================================================================================="
echo " Download last Installation bundle for ${INSTALLER_NAME} and ${VERSION}"
echo " Deploy and Install it to ${ENV} on [${LIST_SSH_SERVER}]"
echo "===================================================================================================="

# Gather installation bundle from Artifactory
if [[ "${MAVEN_REPOSITORY}" == "artifactory" ]]; then
  echo "Search DCES Artifactory for last Installation bundle attached to ${INSTALLER_NAME} and ${VERSION}"
  echo "=== wget -qO- ${ARTIFACTORY_DCES_BASE_URL}/api/search/gavc?a=${INSTALLER_NAME}&v=${VERSION}"
  export RES=`wget -qO- "${ARTIFACTORY_DCES_BASE_URL}/api/search/gavc?a=${INSTALLER_NAME}&v=${VERSION}" | sed 's/\"/\n/g' | grep -oP 'http.?:\/\/.*.tar.gz' | grep -v "doc.tar.gz" | sort -r | head -1`
  if [[ "${RES}" == "" ]]; then
    echo "=== No Artifact found. Searching on IPO Artifactory server last Installation bundle attached to ${ARTIFACT_INSTALL_ID} and ${VERSION}"
    echo "=== wget -qO- ${ARTIFACTORY_IPO_BASE_URL}/api/search/gavc?a=${INSTALLER_NAME}&v=${VERSION}"
    export RES=`wget -qO- "${ARTIFACTORY_IPO_BASE_URL}/api/search/gavc?a=${INSTALLER_NAME}&v=${VERSION}" | sed 's/\"/\n/g' | grep -oP 'http.?:\/\/.*.tar.gz' | grep -v "doc.tar.gz" | sort -r | head -1`
  fi

  if [[ "${RES}" != "" ]]; then
    export TGZ_URL=`echo ${RES}| sed "s|api/storage/||g"`
  fi

  # Error when no url found in both Artifactory
  if [[ "${TGZ_URL}" == "" ]]; then
    error "No Artifact found in DCES and IPO Artifactory for ${INSTALLER_NAME} and ${VERSION}"
  fi

  # Retreve artifact name from Url
  TGZ_NAME=`echo ${TGZ_URL} | sed "s|.*${INSTALLER_NAME}|${INSTALLER_NAME}|g"`
  echo "Artifact found for ${INSTALLER_NAME} and ${VERSION} : ${TGZ_URL}"

  echo "Downloading ${TGZ_URL}"
  wget --no-verbose  --output-document=${TGZ_NAME} ${TGZ_URL}
  RC=$?
  if [[ ${RC} != 0 ]]; then
    error "Error while downloading ${TGZ_URL} in ${REPO_BASEPATH}/${TGZ_NAME}"
  fi
  echo "Artifact is downloaded in ${TGZ_NAME}"

# Gather installation bundle locally
else

  echo "Search Maven Local repo for last Installation bundle attached to ${INSTALLER_NAME} and ${VERSION}"
  TGZ_URL=`find ${MVN_REPO_BASEPATH} -name "${INSTALLER_NAME}*" | grep "${VERSION}" | grep ".tar.gz" | grep -v "doc.tar.gz" | sort -r | head -1`

  # Error when no url found in both Artifactory
  if [[ "${TGZ_URL}" == "" ]]; then
    error "No Artifact locally found for ${INSTALLER_NAME} and ${VERSION}"
  fi

  # Retreve artifact name from Url
  #TGZ_NAME=`echo ${TGZ_URL} | sed "s|.*${INSTALLER_NAME}|${INSTALLER_NAME}|g"`
  echo "TGZ name ${TGZ_NAME}"
  echo "Artifact found for ${INSTALLER_NAME} and ${VERSION} : ${TGZ_URL}"

  echo "Retrieving ${TGZ_URL}"
  cp -v ${TGZ_URL} ${TGZ_NAME}

fi

echo "===================================================================================================="
echo "Deploying and installation the installation bundle on VM"

for ssh_server in ${LIST_SSH_SERVER}; 
do 
    echo "${ssh_server} : Cleanout release folder on target SSH"
    ssh -o StrictHostKeyChecking=no ${ssh_server} "cd /app/release/ && rm -rf *.tar.gz *.sh"

    echo "${ssh_server} : Send ${TGZ_NAME} on ${ssh_server}:/app/release/"
    scp -o StrictHostKeyChecking=no ${TGZ_NAME} ${ssh_server}:/app/release/

    echo "${ssh_server} : Extract ${TGZ_NAME} in ${ssh_server}:/app/release/"
    CMD="export PATH=/opt/java/bin:/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin:/oper/ccm/bin:/oper/ec2/bin:/oper/java/bin:/oper/python27/bin:/root/bin"
    CMD="${CMD} && mkdir -p /app/release/"
    CMD="${CMD} && cd /app/release/"
    CMD="${CMD} && rm -rf ${PROJECT_NAME}-release-${VERSION}"
    CMD="${CMD} && tar xzf ${TGZ_NAME}"
    echo "Executing command ${CMD} on ${ssh_server}"
    ssh -o StrictHostKeyChecking=no ${ssh_server} "${CMD}"
done

echo "===================================================================================================="
echo "Installing release on ${LIST_SSH_SERVER}"
INSTALLATION_FOLDER=`tar tvzf ${TGZ_NAME} | grep project.sh | sed "s|.* ||g" | sed "s|/.*||g"`
echo "INSTA folder ${INSTALLATION_FOLDER}  in env ${ENV}"
for ssh_server in ${LIST_SSH_SERVER}; 
do 
    echo "${ssh_server} : Install release ${PROJECT_NAME} ${release_version} ${ENV}"
    CMD="export PATH=/opt/java/bin:/usr/local/sbin:/usr/local/bin:/sbin:/bin:/usr/sbin:/usr/bin:/oper/ccm/bin:/oper/ec2/bin:/oper/java/bin:/oper/python27/bin:/root/bin"
    CMD="${CMD} && cd /app/release/${INSTALLATION_FOLDER}/"
    CMD="${CMD} && ./install.sh ${ENV} -force -nobackup"
    CMD="${CMD} && exit 0"
    ssh -o StrictHostKeyChecking=no ${ssh_server} "${CMD}"
done

