# Copyright (c) 2022 SUSE LLC
# Licensed under the terms of the MIT license.
#
# The scenarios in this feature are skipped:
# * if there is no proxy ($proxy is nil)
# * if there is no private network ($private_net is nil)
# * if there is no PXE boot minion ($pxeboot_mac is nil)

@scope_containerized_proxy
@proxy
Feature: Register and test a Containerized Proxy
  In order to test Containerized Proxy
  As the system administrator
  I want to register the proxy to the server
