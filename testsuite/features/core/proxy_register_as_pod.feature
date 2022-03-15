# Copyright (c) 2022 SUSE LLC
# Licensed under the terms of the MIT license.
#
# The scenarios in this feature are skipped if there is no proxy
# ($proxy is nil)
#
# Alternative: Bootstrap the proxy as a Salt minion from script

@scope_containerized_proxy
@proxy
Feature: Setup Uyuni containerized proxy
  In order to use a containerized proxy with the Uyuni server
  As the system administrator
  I want to register the containerized proxy to the server
