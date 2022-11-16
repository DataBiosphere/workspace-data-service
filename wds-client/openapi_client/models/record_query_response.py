# coding: utf-8

"""
    Workspace Data Service

    This page lists both current and proposed APIs. The proposed APIs which have not yet been implemented are marked as deprecated. This is incongruous, but by using the deprecated flag, we can force swagger-ui to display those endpoints differently.  Error codes and responses for proposed APIs are likely to change as we gain more clarity on their implementation.  As of v0.2, all APIs are subject to change without notice.   # noqa: E501

    The version of the OpenAPI document: v0.2
    Generated by: https://openapi-generator.tech
"""


import pprint
import re  # noqa: F401

import six

from openapi_client.configuration import Configuration


class RecordQueryResponse(object):
    """NOTE: This class is auto generated by OpenAPI Generator.
    Ref: https://openapi-generator.tech

    Do not edit the class manually.
    """

    """
    Attributes:
      openapi_types (dict): The key is attribute name
                            and the value is attribute type.
      attribute_map (dict): The key is attribute name
                            and the value is json key in definition.
    """
    openapi_types = {
        'search_request': 'SearchRequest',
        'total_records': 'int',
        'records': 'list[RecordResponse]'
    }

    attribute_map = {
        'search_request': 'searchRequest',
        'total_records': 'totalRecords',
        'records': 'records'
    }

    def __init__(self, search_request=None, total_records=None, records=None, local_vars_configuration=None):  # noqa: E501
        """RecordQueryResponse - a model defined in OpenAPI"""  # noqa: E501
        if local_vars_configuration is None:
            local_vars_configuration = Configuration()
        self.local_vars_configuration = local_vars_configuration

        self._search_request = None
        self._total_records = None
        self._records = None
        self.discriminator = None

        self.search_request = search_request
        self.total_records = total_records
        self.records = records

    @property
    def search_request(self):
        """Gets the search_request of this RecordQueryResponse.  # noqa: E501


        :return: The search_request of this RecordQueryResponse.  # noqa: E501
        :rtype: SearchRequest
        """
        return self._search_request

    @search_request.setter
    def search_request(self, search_request):
        """Sets the search_request of this RecordQueryResponse.


        :param search_request: The search_request of this RecordQueryResponse.  # noqa: E501
        :type: SearchRequest
        """
        if self.local_vars_configuration.client_side_validation and search_request is None:  # noqa: E501
            raise ValueError("Invalid value for `search_request`, must not be `None`")  # noqa: E501

        self._search_request = search_request

    @property
    def total_records(self):
        """Gets the total_records of this RecordQueryResponse.  # noqa: E501

        number of records in the record type  # noqa: E501

        :return: The total_records of this RecordQueryResponse.  # noqa: E501
        :rtype: int
        """
        return self._total_records

    @total_records.setter
    def total_records(self, total_records):
        """Sets the total_records of this RecordQueryResponse.

        number of records in the record type  # noqa: E501

        :param total_records: The total_records of this RecordQueryResponse.  # noqa: E501
        :type: int
        """
        if self.local_vars_configuration.client_side_validation and total_records is None:  # noqa: E501
            raise ValueError("Invalid value for `total_records`, must not be `None`")  # noqa: E501

        self._total_records = total_records

    @property
    def records(self):
        """Gets the records of this RecordQueryResponse.  # noqa: E501

        list of records found  # noqa: E501

        :return: The records of this RecordQueryResponse.  # noqa: E501
        :rtype: list[RecordResponse]
        """
        return self._records

    @records.setter
    def records(self, records):
        """Sets the records of this RecordQueryResponse.

        list of records found  # noqa: E501

        :param records: The records of this RecordQueryResponse.  # noqa: E501
        :type: list[RecordResponse]
        """
        if self.local_vars_configuration.client_side_validation and records is None:  # noqa: E501
            raise ValueError("Invalid value for `records`, must not be `None`")  # noqa: E501

        self._records = records

    def to_dict(self):
        """Returns the model properties as a dict"""
        result = {}

        for attr, _ in six.iteritems(self.openapi_types):
            value = getattr(self, attr)
            if isinstance(value, list):
                result[attr] = list(map(
                    lambda x: x.to_dict() if hasattr(x, "to_dict") else x,
                    value
                ))
            elif hasattr(value, "to_dict"):
                result[attr] = value.to_dict()
            elif isinstance(value, dict):
                result[attr] = dict(map(
                    lambda item: (item[0], item[1].to_dict())
                    if hasattr(item[1], "to_dict") else item,
                    value.items()
                ))
            else:
                result[attr] = value

        return result

    def to_str(self):
        """Returns the string representation of the model"""
        return pprint.pformat(self.to_dict())

    def __repr__(self):
        """For `print` and `pprint`"""
        return self.to_str()

    def __eq__(self, other):
        """Returns true if both objects are equal"""
        if not isinstance(other, RecordQueryResponse):
            return False

        return self.to_dict() == other.to_dict()

    def __ne__(self, other):
        """Returns true if both objects are not equal"""
        if not isinstance(other, RecordQueryResponse):
            return True

        return self.to_dict() != other.to_dict()
