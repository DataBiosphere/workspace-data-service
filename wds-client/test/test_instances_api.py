# coding: utf-8

"""
    Workspace Data Service

    This page lists both current and proposed APIs. The proposed APIs which have not yet been implemented are marked as deprecated. This is incongruous, but by using the deprecated flag, we can force swagger-ui to display those endpoints differently.  Error codes and responses for proposed APIs are likely to change as we gain more clarity on their implementation.  As of v0.2, all APIs are subject to change without notice.   # noqa: E501

    The version of the OpenAPI document: v0.2
    Generated by: https://openapi-generator.tech
"""


from __future__ import absolute_import

import unittest

import openapi_client
from openapi_client.api.instances_api import InstancesApi  # noqa: E501
from openapi_client.rest import ApiException


class TestInstancesApi(unittest.TestCase):
    """InstancesApi unit test stubs"""

    def setUp(self):
        self.api = openapi_client.api.instances_api.InstancesApi()  # noqa: E501

    def tearDown(self):
        pass

    def test_create_wds_instance(self):
        """Test case for create_wds_instance

        Create an instance (unstable)  # noqa: E501
        """
        pass


if __name__ == '__main__':
    unittest.main()
