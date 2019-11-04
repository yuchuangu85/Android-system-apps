"""Tests for bugreport_app_tester."""
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from __future__ import unicode_literals

import unittest

import bugreport_app_tester


class DeviceTest(unittest.TestCase):

  def setUp(self):
    self._mock_popen = SimpleMagicMock()  # type: subprocess.Popen object.
    self._mock_popen_fn = SimpleMagicMock(return_value=self._mock_popen)
    bugreport_app_tester.subprocess.Popen = self._mock_popen_fn
    self._subject = bugreport_app_tester.Device(serialno=None)

  def test_read_lines_from_subprocess(self):
    self._mock_popen.stdout.read.return_value = '\n \n asd\r\n\r\nqwe 123 $\n\n'
    result = self._subject._read_lines_from_subprocess(self._mock_popen)
    self.assertEqual(result, ['', ' ', ' asd', '', 'qwe 123 $', '', ''])

  def test_read_lines_from_subprocess_empty(self):
    self._mock_popen.stdout.read.return_value = ''
    result = self._subject._read_lines_from_subprocess(self._mock_popen)
    self.assertEqual(result, [])

  def test_adb_with_serialno(self):
    self._subject = bugreport_app_tester.Device(serialno='serial')
    self._mock_popen.stdout = SimpleMagicMock()
    self._mock_popen.wait.return_value = 0
    self._mock_popen.stdout.read = SimpleMagicMock(return_value='blah')
    exit_code, stdout_lines = self._subject.adb(['some', 'command'])
    self.assertEqual(self._mock_popen_fn.calls[0]['args'][0],
                     ['adb', '-s', 'serial', 'some', 'command'])
    self.assertEqual(exit_code, 0)
    self.assertEqual(stdout_lines, ['blah'])

  def test_is_adb_root_no(self):
    self._mock_popen.wait = SimpleMagicMock(return_value=1)
    self.assertFalse(self._subject.is_adb_root())
    self.assertEqual(self._mock_popen_fn.calls[0]['args'][0],
                     ['adb', 'shell', 'ls', '/data/user/0'])

  def test_is_adb_root_yes(self):
    self._mock_popen.wait.return_value = 0
    self.assertTrue(self._subject.is_adb_root())
    self.assertEqual(self._mock_popen_fn.calls[0]['args'][0],
                     ['adb', 'shell', 'ls', '/data/user/0'])

  def test_pidof(self):
    self._mock_popen.stdout.read.return_value = '123 789'
    self.assertEqual(self._subject.pidof('com.my.package'), [123, 789])
    self.assertEqual(self._mock_popen_fn.calls[0]['args'][0],
                     ['adb', 'shell', 'pidof', 'com.my.package'])


class BugreportAppTesterTest(unittest.TestCase):

  def setUp(self):
    self._mock_device = SimpleMagicMock()
    self._subject = bugreport_app_tester.BugreportAppTester(self._mock_device)
    bugreport_app_tester._fail_program = SimpleMagicMock()

  def test_delete_all_bugreports(self):
    self._subject._delete_all_bugreports()
    self.assertEqual(len(self._mock_device.adbx.calls), 2)
    self.assertEqual(self._mock_device.adbx.calls[0]['args'][0], [
        'shell', 'rm', '-f', '/data/user/0/com.google.android.car.bugreport/'
        'bug_reports_pending/*.zip'
    ])
    self.assertEqual(self._mock_device.adbx.calls[1]['args'][0], [
        'shell', 'sqlite3', '/data/user/0/com.google.android.car.bugreport/'
        'databases/bugreport.db', "'delete from bugreports;'"
    ])

  def test_start_bug_report(self):
    self._subject._start_bug_report()
    self.assertEqual(len(self._mock_device.adbx.calls), 1)
    self.assertEqual(self._mock_device.adbx.calls[0]['args'][0], [
        'shell', 'am', 'start',
        'com.google.android.car.bugreport/.BugReportActivity'
    ])


class SimpleMagicMock(object):
  """Simple unittest.mock.MagicMock implementation.

  Unfortunately unittest.mock exists only in python 3. To support python 2 -
  because most systems come with python 2 by default - we implemented our own
  MagicMock.
  """

  def __init__(self, return_value=None):
    self.calls = []
    self.return_value = return_value

  def __call__(self, *args, **kwargs):
    self.calls.append({'args': args, 'kwargs': kwargs})
    return self.return_value

  def __getattr__(self, name):
    mock_attr = SimpleMagicMock()
    self.__setattr__(name, mock_attr)
    return mock_attr


if __name__ == '__main__':
  # Cheating here, so that we can import bugreport_app_tester.py.
  import sys
  import os
  sys.path.append(
      os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

  unittest.main()
