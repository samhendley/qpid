#
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.
#

# Option to require building optional plugins
foreach (r ${REQUIRE})
  set(${r}_force ON)
  message(STATUS "Forcing ${r} to ${${r}_force}")
endforeach(r)

include(CheckFunctionExists)
include(CheckIncludeFileCXX)
include(CheckIncludeFiles)
include(CheckIncludeFileCXX)
include(CheckLibraryExists)
include(CheckSymbolExists)
include(FindBoost)
include(FindDoxygen)

#set (CMAKE_VERBOSE_MAKEFILE ON)  # for dubugging

# check if we generate source as part of the build
#   - rubygen generates the amqp spec and clustering
#   - managementgen generates the broker management code
#
# rubygen subdir is excluded from stable distributions
# If the main AMQP spec is present, then check if ruby and python are
# present, and if any sources have changed, forcing a re-gen of source code.

set(AMQP_SPEC_DIR ${qpidc_SOURCE_DIR}/../specs)
set(AMQP_SPEC ${AMQP_SPEC_DIR}/amqp.0-10-qpid-errata.xml)
if (EXISTS ${AMQP_SPEC})
  include(FindRuby)
  include(FindPythonInterp)
  if (NOT RUBY_EXECUTABLE)
    message(FATAL_ERROR "Can't locate ruby, needed to generate source files.")
  endif (NOT RUBY_EXECUTABLE)
  if (NOT PYTHON_EXECUTABLE)
    message(FATAL_ERROR "Can't locate python, needed to generate source files.")
  endif (NOT PYTHON_EXECUTABLE)

  set(specs ${AMQP_SPEC} ${qpidc_SOURCE_DIR}/xml/cluster.xml)
  set(regen_amqp OFF)
  set(rgen_dir ${qpidc_SOURCE_DIR}/rubygen)
  file(GLOB_RECURSE rgen_progs ${rgen_dir}/*.rb)
  # If any of the specs, or any of the sources used to generate code, change
  # then regenerate the sources.
  foreach (spec_file ${specs} ${rgen_progs})
    if (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_SOURCE_DIR}/rubygen.cmake)
      set(regen_amqp ON)
    endif (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_SOURCE_DIR}/rubygen.cmake)
  endforeach (spec_file ${specs})
  if (regen_amqp)
    message(STATUS "Regenerating AMQP protocol sources")
    execute_process(COMMAND ${RUBY_EXECUTABLE} -I ${rgen_dir} ${rgen_dir}/generate gen ${specs} all ${CMAKE_CURRENT_SOURCE_DIR}/rubygen.cmake
                    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})
  else (regen_amqp)
    message(STATUS "No need to generate AMQP protocol sources")
  endif (regen_amqp)

  set(mgmt_specs ${AMQP_SPEC_DIR}/management-schema.xml
                 ${CMAKE_CURRENT_SOURCE_DIR}/qpid/acl/management-schema.xml
                 ${CMAKE_CURRENT_SOURCE_DIR}/qpid/cluster/management-schema.xml)
  set(mgen_dir ${qpidc_SOURCE_DIR}/managementgen)
  set(regen_mgmt OFF)
  foreach (spec_file ${mgmt_specs})
    if (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_SOURCE_DIR}/managementgen.cmake)
      message(STATUS "${spec_file} is newer")
      set(regen_mgmt ON)
    endif (${spec_file} IS_NEWER_THAN ${CMAKE_CURRENT_SOURCE_DIR}/managementgen.cmake)
  endforeach (spec_file ${mgmt_specs})
  if (regen_mgmt)
    message(STATUS, "Regenerating Qpid Management Framework sources")
    execute_process(COMMAND ${PYTHON_EXECUTABLE} ${mgen_dir}/qmf-gen -c ${CMAKE_CURRENT_SOURCE_DIR}/managementgen.cmake -b -q -o gen/qmf ${mgmt_specs}
                    WORKING_DIRECTORY ${CMAKE_CURRENT_SOURCE_DIR})
  else (regen_mgmt)
    message(STATUS "No need to generate Qpid Management Framework sources")
  endif (regen_mgmt)

else (EXISTS ${AMQP_SPEC})
  message(STATUS "No AMQP spec... won't generate sources")
endif (EXISTS ${AMQP_SPEC})

# Pull in the names of the generated files, i.e. ${rgen_framing_srcs}
include (rubygen.cmake)
include (managementgen.cmake)

find_program(HELP2MAN help2man DOC "Location of the help2man program")
option(GEN_MANPAGES "Use help2man to generate man pages" ON)
if (GEN_MANPAGES AND NOT HELP2MAN)
  message(STATUS "Can't locate the help2man command; man pages will not be generated")
endif (GEN_MANPAGES AND NOT HELP2MAN)

# FindDoxygen module tries to locate doxygen and Graphviz dot
option(GEN_DOXYGEN "Use doxygen to generate user documentation" ON)
if (GEN_DOXYGEN AND NOT DOXYGEN_EXECUTABLE)
  message(STATUS "Can't locate the doxygen command; user documentation will not be generated")
endif (GEN_DOXYGEN AND NOT DOXYGEN_EXECUTABLE)

find_program(VALGRIND valgrind DOC "Location of the valgrind program")
option(ENABLE_VALGRIND "Use valgrind to detect run-time problems" ON)
if (ENABLE_VALGRIND AND NOT VALGRIND)
  message(STATUS "Can't locate the valgrind command; no run-time error detection")
endif (ENABLE_VALGRIND AND NOT VALGRIND)

set(QPIDC_INSTALL_LIBDIR ${CMAKE_INSTALL_PREFIX}/lib CACHE STRING
    "Directory to install library files")
set(QPIDC_INSTALL_CONFDIR ${CMAKE_INSTALL_PREFIX}/etc CACHE STRING
    "Directory to install configuration files")
set(QPIDC_MODULE_DIR ${QPIDC_INSTALL_LIBDIR}/qpid/client CACHE STRING
    "Directory to load client plug-in modules from")
set(QPIDD_MODULE_DIR ${QPIDC_INSTALL_LIBDIR}/qpid/daemon CACHE STRING
    "Directory to load broker plug-in modules from")
set(QPIDC_CONF_FILE ${QPIDC_INSTALL_CONFDIR}/qpid/qpidc.conf CACHE STRING
    "Name of the Qpid client configuration file")
set(QPIDD_CONF_FILE ${QPIDC_INSTALL_CONFDIR}/qpid/qpidd.conf CACHE STRING
    "Name of the Qpid broker configuration file")

option(ENABLE_WARNINGS "Enable lots of compiler warnings (recommended)" ON)
if (ENABLE_WARNINGS AND CMAKE_COMPILER_IS_GNUCXX)
   # Warnings: Enable as many as possible, keep the code clean. Please
   # do not disable warnings or remove -Werror without discussing on
   # qpid-dev list.
   #
   # The following warnings are deliberately omitted, they warn on valid code.
   # -Wunreachable-code -Wpadded -Winline
   # -Wshadow - warns about boost headers.
   set (WARNING_FLAGS
        " -Werror -pedantic -Wall -Wextra -Wno-shadow -Wpointer-arith -Wcast-qual -Wcast-align -Wno-long-long -Wvolatile-register-var -Winvalid-pch -Wno-system-headers -Woverloaded-virtual")
   set (CMAKE_CXX_FLAGS ${CMAKE_CXX_FLAGS}${WARNING_FLAGS})
endif (ENABLE_WARNINGS AND CMAKE_COMPILER_IS_GNUCXX)

# Expand a bit from the basic Find_Boost; be specific about what's needed.
find_package(Boost 1.33 REQUIRED
             COMPONENTS date_time program_options regex thread unit_test_framework)
if(NOT Boost_FOUND)
  message(FATAL_ERROR "Boost C++ libraries not found.  Please install or try setting BOOST_ROOT")
endif(NOT Boost_FOUND)

include_directories( ${Boost_INCLUDE_DIR} )

include_directories( . )
include_directories( gen )
include_directories( ${CMAKE_CURRENT_BINARY_DIR} )

link_directories( ${Boost_LIBRARY_DIRS} )

CHECK_LIBRARY_EXISTS (rt clock_gettime "" CLOCK_GETTIME_IN_RT)
if (NOT CLOCK_GETTIME_IN_RT)
  CHECK_FUNCTION_EXISTS (clock_gettime QPID_HAS_CLOCK_GETTIME)
else (NOT CLOCK_GETTIME_IN_RT)
  set(CMAKE_REQUIRED_LIBS ${CMAKE_REQUIRED_LIBS} rt)
  set(QPID_HAS_CLOCK_GETTIME YES CACHE BOOL "Platform has clock_gettime")
endif (NOT CLOCK_GETTIME_IN_RT)

# See if Cyrus SASL is desired and available
CHECK_LIBRARY_EXISTS (sasl2 sasl_checkpass "" HAVE_SASL2)
CHECK_INCLUDE_FILES (sasl/sasl.h HAVE_SASL_H)

set (sasl_default ${sasl_force})
if (HAVE_SASL2 AND HAVE_SASL_H)
  set (sasl_default ON)
endif (HAVE_SASL2 AND HAVE_SASL_H)

option(BUILD_SASL "Build with Cyrus SASL support" ${sasl_default})
if (BUILD_SASL)
  if (NOT HAVE_SASL2)
    message(FATAL_ERROR "Cyrus SASL support requested but libsasl2 not found")
  endif (NOT HAVE_SASL2)
  if (NOT HAVE_SASL_H)
    message(FATAL_ERROR "Cyrus SASL support requested but sasl.h not found")
  endif (NOT HAVE_SASL_H)

  set(BROKER_SASL_NAME "qpidd" CACHE STRING "SASL app name for the qpid broker")
  set(qpidcommon_sasl_source
      qpid/sys/cyrus/CyrusSecurityLayer.h
      qpid/sys/cyrus/CyrusSecurityLayer.cpp
     )
  set(qpidcommon_sasl_lib sasl2)
endif (BUILD_SASL)

# See if XML Exchange is desired and prerequisites are available
CHECK_LIBRARY_EXISTS (xerces-c _init "" HAVE_XERCES)
CHECK_INCLUDE_FILE_CXX (xercesc/framework/MemBufInputSource.hpp HAVE_XERCES_H)
CHECK_INCLUDE_FILE_CXX (xqilla/xqilla-simple.hpp HAVE_XQILLA_H)

set (xml_default ${xml_force})
if (CMAKE_SYSTEM_NAME STREQUAL Windows)
else (CMAKE_SYSTEM_NAME STREQUAL Windows)
  if (HAVE_XERCES AND HAVE_XERCES_H)
    if (HAVE_XQILLA_H)
      set (xml_default ON)
    endif (HAVE_XQILLA_H)
  endif (HAVE_XERCES AND HAVE_XERCES_H)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

option(BUILD_XML "Build with XML Exchange" ${xml_default})
if (BUILD_XML)
  if (NOT HAVE_XERCES)
    message(FATAL_ERROR "XML Exchange support requested but xerces-c library not found")
  endif (NOT HAVE_XERCES)
  if (NOT HAVE_XERCES_H)
    message(FATAL_ERROR "XML Exchange support requested but Xerces-C headers not found")
  endif (NOT HAVE_XERCES_H)
  if (NOT HAVE_XQILLA_H)
    message(FATAL_ERROR "XML Exchange support requested but XQilla headers not found")
  endif (NOT HAVE_XQILLA_H)

  add_library (xml MODULE
               qpid/xml/XmlExchange.cpp
               qpid/xml/XmlExchange.h
               qpid/xml/XmlExchangePlugin.cpp)
  target_link_libraries (xml xerces-c xqilla qpidbroker pthread)
  if (CMAKE_COMPILER_IS_GNUCXX)
    set_target_properties (xml PROPERTIES
                           PREFIX ""
                           LINK_FLAGS -Wl,--no-undefined)
  endif (CMAKE_COMPILER_IS_GNUCXX)

  set(xml_tests XmlClientSessionTest)

endif (BUILD_XML)

# Build the ACL plugin
set (acl_default ON)
# Like this until we fix exporting symbols from the generated management code
if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  set(acl_default OFF)
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)
option(BUILD_ACL "Build ACL enforcement broker plugin" ${acl_default})
if (BUILD_ACL)
  set (acl_SOURCES
       qpid/acl/Acl.cpp
       qpid/acl/Acl.h
       qpid/acl/AclData.cpp
       qpid/acl/AclData.h
       qpid/acl/AclPlugin.cpp
       qpid/acl/AclReader.cpp
       qpid/acl/AclReader.h
      )
  add_library (acl MODULE ${acl_SOURCES})
  target_link_libraries (acl qpidbroker)
  if (CMAKE_COMPILER_IS_GNUCXX)
    set_target_properties (acl PROPERTIES
                           PREFIX ""
                           LINK_FLAGS -Wl,--no-undefined)
  endif (CMAKE_COMPILER_IS_GNUCXX)
endif (BUILD_ACL)

# Check for optional cluster support requirements
include (cluster.cmake)

# Check for optional RDMA support requirements
include (rdma.cmake)

# Check for optional SSL support requirements
include (ssl.cmake)

# Check for syslog capabilities not present on all systems
check_symbol_exists (LOG_AUTHPRIV "sys/syslog.h" HAVE_LOG_AUTHPRIV)
check_symbol_exists (LOG_FTP "sys/syslog.h" HAVE_LOG_FTP)

if (CMAKE_SYSTEM_NAME STREQUAL Windows)
  if (MSVC)
    add_definitions( 
      /D "_CRT_NONSTDC_NO_WARNINGS"
      /D "NOMINMAX"
      /D "WIN32_LEAN_AND_MEAN"
      /D "_SCL_SECURE_NO_WARNINGS"
      /wd4244
      /wd4800
      /wd4355
    )
  endif (MSVC)

  set (libqpidcommon_platform_SOURCES
    qpid/log/windows/SinkOptions.cpp
    qpid/sys/windows/AsynchIO.cpp
    qpid/sys/windows/FileSysDir.cpp
    qpid/sys/windows/IocpPoller.cpp
    qpid/sys/windows/IOHandle.cpp
    qpid/sys/windows/LockFile.cpp
    qpid/sys/windows/PipeHandle.cpp
    qpid/sys/windows/PollableCondition.cpp
    qpid/sys/windows/Shlib.cpp
    qpid/sys/windows/Socket.cpp
    qpid/sys/windows/StrError.cpp
    qpid/sys/windows/SystemInfo.cpp
    qpid/sys/windows/Thread.cpp
    qpid/sys/windows/Time.cpp
    qpid/sys/windows/uuid.cpp
  )
  set (libqpidcommon_platform_LIBS
    rpcrt4 ws2_32
  )
  set (libqpidbroker_platform_SOURCES
    qpid/broker/windows/BrokerDefaults.cpp
    qpid/broker/windows/SaslAuthenticator.cpp
  )

  set (libqpidclient_platform_SOURCES
    qpid/client/windows/SaslFactory.cpp
  )

  set (qpidd_platform_SOURCES
    windows/QpiddBroker.cpp
  )

else (CMAKE_SYSTEM_NAME STREQUAL Windows)

  # POSIX (Non-Windows) platforms have a lot of overlap in sources; the only
  # major difference is the poller module.
  if (CMAKE_SYSTEM_NAME STREQUAL Linux)
    set (qpid_poller_module qpid/sys/epoll/EpollPoller.cpp)
    add_definitions(-pthread)
    set (CMAKE_EXE_LINKER_FLAGS ${CMAKE_EXE_LINKER_FLAGS} -pthread)
  endif (CMAKE_SYSTEM_NAME STREQUAL Linux)
  if (CMAKE_SYSTEM_NAME STREQUAL SunOS)
    set (qpid_poller_module qpid/sys/solaris/ECFPoller.cpp)
  endif (CMAKE_SYSTEM_NAME STREQUAL SunOS)

  set (libqpidcommon_platform_SOURCES
    qpid/sys/posix/AsynchIO.cpp
    qpid/sys/posix/Fork.cpp
    qpid/sys/posix/FileSysDir.cpp
    qpid/sys/posix/IOHandle.cpp
    qpid/sys/posix/LockFile.cpp
    qpid/sys/posix/Mutex.cpp
    qpid/sys/posix/PipeHandle.cpp
    qpid/sys/posix/PollableCondition.cpp
    qpid/sys/posix/Shlib.cpp
    qpid/log/posix/SinkOptions.cpp
    qpid/sys/posix/Socket.cpp
    qpid/sys/posix/StrError.cpp
    qpid/sys/posix/SystemInfo.cpp
    qpid/sys/posix/Thread.cpp
    qpid/sys/posix/Time.cpp

    ${qpid_poller_module}
  )
  set (libqpidcommon_platform_LIBS
    ${Boost_program_options_LIBRARY}
    ${Boost_filesystem_LIBRARY}
    uuid
    ${CMAKE_DL_LIBS}
  )

  set (libqpidbroker_platform_SOURCES
     qpid/broker/Daemon.cpp
     qpid/broker/SaslAuthenticator.cpp
     qpid/broker/SignalHandler.h
     qpid/broker/SignalHandler.cpp
     qpid/broker/posix/BrokerDefaults.cpp
  )

  set (libqpidclient_platform_SOURCES
     qpid/client/SaslFactory.cpp
  )

  set (qpidd_platform_SOURCES
    posix/QpiddBroker.cpp
  )
endif (CMAKE_SYSTEM_NAME STREQUAL Windows)

set (libqpidcommon_SOURCES
     ${rgen_framing_srcs}
     ${libqpidcommon_platform_SOURCES}
     ${qpidcommon_sasl_source}
     qpid/assert.cpp
     qpid/Address.cpp
     qpid/DataDir.cpp
     qpid/Exception.cpp
     qpid/Modules.cpp
     qpid/Options.cpp
     qpid/Plugin.cpp
     qpid/RefCountedBuffer.cpp
     qpid/SessionState.cpp
     qpid/SessionId.cpp
     qpid/StringUtils.cpp
     qpid/Url.cpp
     qpid/amqp_0_10/SessionHandler.cpp
     qpid/framing/AccumulatedAck.cpp
     qpid/framing/AMQBody.cpp
     qpid/framing/AMQMethodBody.cpp
     qpid/framing/AMQContentBody.cpp
     qpid/framing/AMQFrame.cpp
     qpid/framing/AMQHeaderBody.cpp
     qpid/framing/AMQHeartbeatBody.cpp
     qpid/framing/Array.cpp
     qpid/framing/BodyHolder.cpp
     qpid/framing/BodyHandler.cpp
     qpid/framing/Buffer.cpp
     qpid/framing/Endian.cpp
     qpid/framing/FieldTable.cpp
     qpid/framing/FieldValue.cpp
     qpid/framing/FrameSet.cpp
     qpid/framing/FrameDecoder.cpp
     qpid/framing/ProtocolInitiation.cpp
     qpid/framing/ProtocolVersion.cpp
     qpid/framing/SendContent.cpp
     qpid/framing/SequenceNumber.cpp
     qpid/framing/SequenceNumberSet.cpp
     qpid/framing/SequenceSet.cpp
     qpid/framing/Proxy.cpp
     qpid/framing/Uuid.cpp
     qpid/framing/TransferContent.cpp
     qpid/log/Logger.cpp
     qpid/log/Options.cpp
     qpid/log/OstreamOutput.cpp
     qpid/log/Selector.cpp
     qpid/log/Statement.cpp
     qpid/management/Manageable.cpp
     qpid/management/ManagementObject.cpp
     qpid/sys/AggregateOutput.cpp
     qpid/sys/AsynchIOHandler.cpp
     qpid/sys/Dispatcher.cpp
     qpid/sys/DispatchHandle.cpp
     qpid/sys/Runnable.cpp
     qpid/sys/Shlib.cpp
     qpid/sys/Timer.cpp
)
add_library (qpidcommon SHARED ${libqpidcommon_SOURCES})
if (CLOCK_GETTIME_IN_RT)
  set (libqpidcommon_platform_LIBS ${libqpidcommon_platform_LIBS} rt)
endif (CLOCK_GETTIME_IN_RT)
target_link_libraries (qpidcommon
                       ${libqpidcommon_platform_LIBS}
                       ${qpidcommon_sasl_lib})
set_target_properties (qpidcommon PROPERTIES
                       VERSION ${qpidc_version})

set (libqpidclient_SOURCES
     ${rgen_client_srcs}
     ${libqpidclient_platform_SOURCES}
     qpid/client/Bounds.cpp
     qpid/client/Completion.cpp
     qpid/client/Connection.cpp
     qpid/client/ConnectionHandler.cpp
     qpid/client/ConnectionImpl.cpp
     qpid/client/ConnectionSettings.cpp
     qpid/client/Connector.cpp
     qpid/client/Demux.cpp
     qpid/client/Dispatcher.cpp
     qpid/client/FailoverManager.cpp
     qpid/client/FailoverListener.h
     qpid/client/FailoverListener.cpp
     qpid/client/Future.cpp
     qpid/client/FutureCompletion.cpp
     qpid/client/FutureResult.cpp
     qpid/client/LoadPlugins.cpp
     qpid/client/LocalQueue.cpp
     qpid/client/LocalQueueImpl.cpp
     qpid/client/Message.cpp
     qpid/client/MessageImpl.cpp
     qpid/client/MessageListener.cpp
     qpid/client/MessageReplayTracker.cpp
     qpid/client/QueueOptions.cpp
     qpid/client/Results.cpp
     qpid/client/SessionBase_0_10.cpp
     qpid/client/SessionBase_0_10.h
     qpid/client/SessionBase_0_10Access.h
     qpid/client/ConnectionAccess.h
     qpid/client/SessionImpl.cpp
     qpid/client/StateManager.cpp
     qpid/client/Subscription.cpp
     qpid/client/SubscriptionImpl.cpp
     qpid/client/SubscriptionManager.cpp
     qpid/client/SubscriptionManagerImpl.cpp
)
add_library (qpidclient SHARED ${libqpidclient_SOURCES})
target_link_libraries (qpidclient qpidcommon)
set_target_properties (qpidclient PROPERTIES VERSION ${qpidc_version})

set (libqpidbroker_SOURCES
     ${mgen_broker_cpp}
     ${libqpidbroker_platform_SOURCES}
     qpid/amqp_0_10/Connection.h
     qpid/amqp_0_10/Connection.cpp
     qpid/broker/Broker.cpp
     qpid/broker/BrokerSingleton.cpp
     qpid/broker/Exchange.cpp
     qpid/broker/ExpiryPolicy.cpp
     qpid/broker/Queue.cpp
     qpid/broker/QueueCleaner.cpp
     qpid/broker/QueueListeners.cpp
     qpid/broker/PersistableMessage.cpp
     qpid/broker/Bridge.cpp
     qpid/broker/Connection.cpp
     qpid/broker/ConnectionHandler.cpp
     qpid/broker/ConnectionFactory.cpp
     qpid/broker/DeliverableMessage.cpp
     qpid/broker/DeliveryRecord.cpp
     qpid/broker/DirectExchange.cpp
     qpid/broker/DtxAck.cpp
     qpid/broker/DtxBuffer.cpp
     qpid/broker/DtxManager.cpp
     qpid/broker/DtxTimeout.cpp
     qpid/broker/DtxWorkRecord.cpp
     qpid/broker/ExchangeRegistry.cpp
     qpid/broker/FanOutExchange.cpp
     qpid/broker/HeadersExchange.cpp
     qpid/broker/IncompleteMessageList.cpp
     qpid/broker/Link.cpp
     qpid/broker/LinkRegistry.cpp
     qpid/broker/Message.cpp
     qpid/broker/MessageAdapter.cpp
     qpid/broker/MessageBuilder.cpp
     qpid/broker/MessageStoreModule.cpp
     qpid/broker/NameGenerator.cpp
     qpid/broker/NullMessageStore.cpp
     qpid/broker/QueueBindings.cpp
     qpid/broker/QueueEvents.cpp
     qpid/broker/QueuePolicy.cpp
     qpid/broker/QueueRegistry.cpp
     qpid/broker/RateTracker.cpp
     qpid/broker/RecoveryManagerImpl.cpp
     qpid/broker/RecoveredEnqueue.cpp
     qpid/broker/RecoveredDequeue.cpp
     qpid/broker/RetryList.cpp
     qpid/broker/SecureConnection.cpp
     qpid/broker/SecureConnectionFactory.cpp
     qpid/broker/SemanticState.h
     qpid/broker/SemanticState.cpp
     qpid/broker/SessionAdapter.cpp
     qpid/broker/SessionState.h
     qpid/broker/SessionState.cpp
     qpid/broker/SessionManager.h
     qpid/broker/SessionManager.cpp
     qpid/broker/SessionContext.h
     qpid/broker/SessionHandler.h
     qpid/broker/SessionHandler.cpp
     qpid/broker/System.cpp
     qpid/broker/Timer.cpp
     qpid/broker/TopicExchange.cpp
     qpid/broker/TxAccept.cpp
     qpid/broker/TxBuffer.cpp
     qpid/broker/TxPublish.cpp
     qpid/broker/Vhost.cpp
     qpid/management/ManagementAgent.cpp
     qpid/management/ManagementExchange.cpp
     qpid/sys/TCPIOPlugin.cpp
)
add_library (qpidbroker SHARED ${libqpidbroker_SOURCES})
target_link_libraries (qpidbroker qpidcommon)
set_target_properties (qpidbroker PROPERTIES VERSION ${qpidc_version})
if (MSVC)
  set_target_properties (qpidbroker PROPERTIES COMPILE_FLAGS /wd4290)
endif (MSVC)

set (qpidd_SOURCES
     ${qpidd_platform_SOURCES}
     qpidd.cpp
     qpidd.h
)
add_executable (qpidd ${qpidd_SOURCES})
target_link_libraries (qpidd qpidbroker qpidcommon)

# QMF agent library
#module_hdr += \
#  qpid/agent/ManagementAgent.h \
#  qpid/agent/ManagementAgentImpl.h
set (qmfagent_SOURCES
     qpid/agent/ManagementAgent.h
     qpid/agent/ManagementAgentImpl.cpp
     qpid/agent/ManagementAgentImpl.h
     qmf/Agent.cpp
    )
add_library (qmfagent SHARED ${qmfagent_SOURCES})
target_link_libraries (qmfagent qmfcommon)
set_target_properties (qmfagent PROPERTIES
                       VERSION ${qpidc_version})

set (qmfcommon_SOURCES
    qmf/Agent.cpp
    qmf/ResilientConnection.cpp
    qmf/MessageImpl.cpp
    qmf/SchemaImpl.cpp
    qmf/ValueImpl.cpp
    qmf/ObjectIdImpl.cpp
    qmf/ObjectImpl.cpp
    qmf/QueryImpl.cpp
    )
add_library (qmfcommon SHARED ${qmfcommon_SOURCES})
target_link_libraries (qmfcommon qpidclient)
set_target_properties (qmfcommon PROPERTIES
                       VERSION ${qpidc_version})

# QMF console library
#module_hdr += \
#  qpid/console/Agent.h \
#  qpid/console/Broker.h \
#  qpid/console/ClassKey.h \
#  qpid/console/ConsoleImportExport.h \
#  qpid/console/ConsoleListener.h \
#  qpid/console/Event.h \
#  qpid/console/Object.h \
#  qpid/console/ObjectId.h \
#  qpid/console/Package.h \
#  qpid/console/Schema.h \
#  qpid/console/SequenceManager.h \
#  qpid/console/SessionManager.h \
#  qpid/console/Value.h
set (qmfconsole_SOURCES
     qpid/console/Agent.h
     qpid/console/Agent.cpp
     qpid/console/Broker.h
     qpid/console/Broker.cpp
     qpid/console/ClassKey.h
     qpid/console/ClassKey.cpp
     qpid/console/ConsoleImportExport.h
     qpid/console/ConsoleListener.h
     qpid/console/Event.h
     qpid/console/Event.cpp
     qpid/console/Object.h
     qpid/console/Object.cpp
     qpid/console/ObjectId.h
     qpid/console/ObjectId.cpp
     qpid/console/Package.h
     qpid/console/Package.cpp
     qpid/console/Schema.h
     qpid/console/Schema.cpp
     qpid/console/SequenceManager.h
     qpid/console/SequenceManager.cpp
     qpid/console/SessionManager.h
     qpid/console/SessionManager.cpp
     qpid/console/Value.h
     qpid/console/Value.cpp
    )
add_library (qmfconsole SHARED ${qmfconsole_SOURCES})
target_link_libraries (qmfconsole qpidclient)
set_target_properties (qmfconsole PROPERTIES
                       VERSION ${qpidc_version})

# A queue event listener plugin that creates messages on a replication
# queue corresponding to enqueue and dequeue events:
add_library (replicating_listener MODULE
             qpid/replication/constants.h
             qpid/replication/ReplicatingEventListener.cpp
             qpid/replication/ReplicatingEventListener.h
            )
target_link_libraries (replicating_listener qpidbroker)
set_target_properties (replicating_listener PROPERTIES
                       PREFIX "")
if (CMAKE_COMPILER_IS_GNUCXX)
  set_target_properties(replicating_listener PROPERTIES
                        LINK_FLAGS -Wl,--no-undefined)
endif (CMAKE_COMPILER_IS_GNUCXX)

# A custom exchange plugin that allows an exchange to be created that
# can process the messages from a replication queue (populated on the
# source system by the replicating listener plugin above) and take the
# corresponding action on the local queues
add_library (replicating_exchange MODULE
             qpid/replication/constants.h
             qpid/replication/ReplicationExchange.cpp
             qpid/replication/ReplicationExchange.h
            )
target_link_libraries (replicating_exchange qpidbroker)
set_target_properties (replicating_exchange PROPERTIES
                       PREFIX "")
if (CMAKE_COMPILER_IS_GNUCXX)
  set_target_properties(replicating_exchange PROPERTIES
                        LINK_FLAGS -Wl,--no-undefined)
endif (CMAKE_COMPILER_IS_GNUCXX)

# This is only really needed until all the trunk builds (Linux, UNIX, Windows)
# are all on cmake only. This is because cmake builds always have a config.h
# file whereas older builds only have config.h on autoconf-generated builds.
add_definitions(-DHAVE_CONFIG_H)

# Now create the config file from all the info learned above.
configure_file(${CMAKE_CURRENT_SOURCE_DIR}/config.h.cmake
               ${CMAKE_CURRENT_BINARY_DIR}/config.h)

# tests has EXCLUDE_FROM_ALL to avoid building and running tests during
# "make all". Top-level CMakeLists has a "check" target that runs the tests
# and all the tests are dependencies of "check" so they'll get built then.
add_subdirectory(tests EXCLUDE_FROM_ALL)