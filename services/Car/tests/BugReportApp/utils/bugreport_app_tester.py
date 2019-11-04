#!/usr/bin/env python
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
"""Semi-automatic AAE BugReport App test utility.

It automates most of mundane steps when testing AAE BugReport app, but still
requires manual input from a tester.

How it works:
1. Runs adb as root.
2. Enables airplane mode to disable Internet.
3. Delete all the old bug reports.
4. Starts BugReport activity.
5. Waits 15 seconds and gets MetaBugReport from sqlite3.
6. Waits until dumpstate finishes. Timeouts after 10 minutes.
7. Writes bugreport, image and audio files to `bugreport-app-data/` directory.
8. Disables airplane mode to enable Internet.
9. Waits until bugreport is uploaded. Timeouts after 3 minutes.
10. Prints results.
"""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function
from __future__ import unicode_literals

import argparse
from collections import namedtuple
import os
import re
import subprocess
import sys
import shutil
import sqlite3
import tempfile
import time
import zipfile

VERSION = '0.2.0'

BUGREPORT_PACKAGE = 'com.google.android.car.bugreport'
PENDING_BUGREPORTS_DIR = ('/data/user/0/%s/bug_reports_pending' %
                          BUGREPORT_PACKAGE)
SQLITE_DB_DIR = '/data/user/0/%s/databases' % BUGREPORT_PACKAGE
SQLITE_DB_PATH = SQLITE_DB_DIR + '/bugreport.db'

# The statuses are from `src/com/google/android/car/bugreport/Status.java.
STATUS_WRITE_PENDING = 0
STATUS_WRITE_FAILED = 1
STATUS_UPLOAD_PENDING = 2
STATUS_UPLOAD_SUCCESS = 3
STATUS_UPLOAD_FAILED = 4
STATUS_USER_CANCELLED = 5

DUMPSTATE_DEADLINE_SEC = 300  # 10 minutes.
UPLOAD_DEADLINE_SEC = 180  # 3 minutes.
CHECK_STATUS_EVERY_SEC = 15  # Check status every 15 seconds.
# Give BuigReport App 15 seconds to initialize after starting voice recording.
META_BUGREPORT_WAIT_TIME_SEC = 15
BUGREPORT_STATUS_POLL_TICK = 1  # Tick every 1 second

# Regex to parse android build property lines from dumpstate (bugreport).
PROP_LINE_RE = re.compile(r'^\[(.+)\]: \[(.+)\]$')

# Holds bugreport info. See MetaBugReport.java.
MetaBugReport = namedtuple(
    'MetaBugReport',
    ['id', 'timestamp', 'filepath', 'status', 'status_message'])

# Holds a file from a zip file.
#
# Properties:
#   name : str - filename.
#   content : bytes - content of the file.
#   size : int - real size of the file.
#   compress_size : int - compressed size of the file.
File = namedtuple('File', ['name', 'content', 'size', 'compress_size'])

# Android Build Properties extract from dumpstate (bugreport) results.
BuildProperties = namedtuple('BuildProperties', ['fingerprint'])


def _red(msg):
  return '\033[31m%s\033[0m' % msg


def _green(msg):
  return '\033[32m%s\033[0m' % msg


def _fail_program(msg):
  """Prints error message and exits the program."""
  print(_red(msg))
  exit(1)


def _bugreport_status_to_str(status):
  """Returns string representation of a bugreport status."""
  if status == STATUS_WRITE_PENDING:
    return 'WRITE_PENDING'
  elif status == STATUS_WRITE_FAILED:
    return 'WRITE_FAILED'
  elif status == STATUS_UPLOAD_PENDING:
    return 'UPLOAD_PENDING'
  elif status == STATUS_UPLOAD_SUCCESS:
    return 'UPLOAD_SUCCESS'
  elif status == STATUS_UPLOAD_FAILED:
    return 'UPLOAD_FAILED'
  elif status == STATUS_USER_CANCELLED:
    return 'USER_CANCELLED'
  return 'UNKNOWN_STATUS'


class Device(object):

  def __init__(self, serialno):
    """Initializes BugreportAppTester.

    Args:
      serialno : Optional[str] - an android device serial number.
    """
    self._serialno = serialno

  def _read_lines_from_subprocess(self, popen):
    """Reads lines from subprocess.Popen."""
    raw = popen.stdout.read()
    try:
      converted = str(raw, 'utf-8')
    except TypeError:
      converted = str(raw)
    if not converted:
      return []
    lines = re.split(r'\r?\n', converted)
    return lines

  def adb(self, cmd):
    """Runs adb command on the device.

    adb's stderr is redirected to this program's stderr.

    Arguments:
      cmd : List[str] - adb command and a list of arguments.

    Returns:
      Tuple[int, List[str]] - exit code and lines from the stdout of the
                              command.
    """
    if self._serialno:
      full_cmd = ['adb', '-s', self._serialno] + cmd
    else:
      full_cmd = ['adb'] + cmd
    popen = subprocess.Popen(full_cmd, stdout=subprocess.PIPE)
    stdout_lines = self._read_lines_from_subprocess(popen)
    exit_code = popen.wait()
    return (exit_code, stdout_lines)

  def adbx(self, cmd):
    """Runs adb command on the device, it fails the program is the cmd fails.

    Arguments:
      cmd : List[str] - adb command and a list of arguments.

    Returns:
      List[str] - lines from the stdout of the command.
    """
    exit_code, stdout_lines = self.adb(cmd)
    if exit_code != 0:
      _fail_program('Failed to run command %s, exit_code=%s' % (cmd, exit_code))
    return stdout_lines

  def is_adb_root(self):
    """Checks if the adb is running as root."""
    return self.adb(['shell', 'ls', '/data/user/0'])[0] == 0

  def restart_adb_as_root(self):
    """Restarts adb as root."""
    if not self.is_adb_root():
      print("adb is not running as root. Running 'adb root'.")
      self.adbx(['root'])

  def pidof(self, package):
    """Returns a list of PIDs for the package."""
    _, lines = self.adb(['shell', 'pidof', package])
    if not lines:
      return None
    pids_raw = [pid.strip() for pid in re.split(r'\s+', ' '.join(lines))]
    return [int(pid) for pid in pids_raw if pid]

  def disable_internet(self):
    """Disables the Internet on the device."""
    print('\nDisabling the Internet.')
    # NOTE: Need to run all these commands, otherwise sometimes airplane mode
    #       doesn't enabled.
    self.adbx(['shell', 'svc', 'wifi', 'disable'])
    self.adbx(['shell', 'settings', 'put', 'global', 'airplane_mode_on', '1'])
    self.adbx([
        'shell', 'am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE',
        '--ez', 'state', 'true'
    ])

  def enable_internet(self):
    """Enables the Internet on the device."""
    print('\nEnabling the Internet.')
    self.adbx(['shell', 'settings', 'put', 'global', 'airplane_mode_on', '0'])
    self.adbx([
        'shell', 'am', 'broadcast', '-a', 'android.intent.action.AIRPLANE_MODE',
        '--ez', 'state', 'false'
    ])
    self.adbx(['shell', 'svc', 'wifi', 'enable'])


class BugreportAppTester(object):

  def __init__(self, device):
    """Initializes BugreportAppTester.

    Args:
      device : Device - an android device.
    """
    self._device = device

  def _kill_bugreport_app(self):
    """Kills the BugReport App is it's running."""
    pids = self._device.pidof(BUGREPORT_PACKAGE)
    if not pids:
      return
    for pid in pids:
      print('Killing bugreport app with pid %d' % pid)
      self._device.adb(['shell', 'kill', str(pid)])

  def _delete_all_bugreports(self):
    """Deletes old zip files and bugreport entries in sqlite3."""
    print('Deleting old bugreports from the device...')
    self._device.adb(['shell', 'rm', '-f', PENDING_BUGREPORTS_DIR + '/*.zip'])
    self._device.adb(
        ['shell', 'sqlite3', SQLITE_DB_PATH, '\'delete from bugreports;\''])

  def _start_bug_report(self):
    """Starts BugReportActivity."""
    self._device.adbx(
        ['shell', 'am', 'start', BUGREPORT_PACKAGE + '/.BugReportActivity'])

  def _get_meta_bugreports(self):
    """Returns bugreports from sqlite3 as a list of MetaBugReport."""
    tmpdir = tempfile.mkdtemp(prefix='aae-bugreport-', suffix='db')
    exit_code, stdout_lines = self._device.adb(['pull', SQLITE_DB_DIR, tmpdir])
    if exit_code != 0:
      shutil.rmtree(tmpdir, ignore_errors=True)
      _fail_program('Failed to pull bugreport.db, msg=%s, exit_code=%s' %
                    (stdout_lines, exit_code))
    conn = sqlite3.connect(os.path.join(tmpdir, 'databases/bugreport.db'))
    c = conn.cursor()
    c.execute('select * from bugreports')
    meta_bugreports = []
    # See BugStorageProvider.java for column indicies.
    for row in c.fetchall():
      meta_bugreports.append(
          MetaBugReport(
              id=row[0],
              timestamp=row[3],
              filepath=row[5],
              status=row[6],
              status_message=row[7]))
    conn.close()
    shutil.rmtree(tmpdir, ignore_errors=True)
    return meta_bugreports

  def _get_active_bugreport(self):
    """Returns current active MetaBugReport."""
    bugreports = self._get_meta_bugreports()
    if len(bugreports) != 1:
      _fail_program('Failure. Expected only 1 bugreport, but there are %d '
                    'bugreports' % len(bugreports))
    return bugreports[0]

  def _wait_for_bugreport_status_to_change_to(self,
                                              expected_status,
                                              deadline_sec,
                                              bugreport_id,
                                              allowed_statuses=[],
                                              fail=False):
    """Waits until status changes to expected_status.

    Args:
      expected_status : int - wait until status changes to this.
      deadline_sec : float - how long to wait, fails if deadline reaches.
      bugreport_id : int - bugreport to check.
      allowed_statuses : List[int] - if the status changes to something else
        than allowed_statuses, it fails.
      fail : bool - exit the program if conditions don't meet.

    Returns:
      if succeeds it returns None. If fails it returns error message.
    """
    timeout_at = time.time() + deadline_sec
    last_fetch_at = time.time()
    while time.time() < timeout_at:
      remaining = timeout_at - time.time()
      sys.stdout.write('Remaining time %.0f seconds\r' % remaining)
      sys.stdout.flush()
      time.sleep(BUGREPORT_STATUS_POLL_TICK)
      if time.time() - last_fetch_at < CHECK_STATUS_EVERY_SEC:
        continue
      last_fetch_at = time.time()
      bugreports = self._get_meta_bugreports()
      meta_bugreport = next(
          iter([b for b in bugreports if b.id == bugreport_id]), None)
      if not meta_bugreport:
        print()  # new line to preserve the progress on terminal.
        return 'Bugreport with id %d not found' % bugreport_id
      if meta_bugreport.status in allowed_statuses:
        # Expected, waiting for status to change.
        pass
      elif meta_bugreport.status == expected_status:
        print()  # new line to preserve the progress on terminal.
        return None
      else:
        expected_str = _bugreport_status_to_str(expected_status)
        actual_str = _bugreport_status_to_str(meta_bugreport.status)
        print()  # new line to preserve the progress on terminal.
        return ('Expected status to be %s, but got %s. Message: %s' %
                (expected_str, actual_str, meta_bugreport.status_message))
    print()  # new line to preserve the progress on terminal.
    return ('Timeout, status=%s' %
            _bugreport_status_to_str(meta_bugreport.status))

  def _wait_for_bugreport_to_complete(self, bugreport_id):
    """Waits until status changes to UPLOAD_PENDING.

    It means dumpstate (bugreport) is completed (or failed).

    Args:
      bugreport_id : int - MetaBugReport id.
    """
    print('\nWaiting until the bug report is collected.')
    err_msg = self._wait_for_bugreport_status_to_change_to(
        STATUS_UPLOAD_PENDING,
        DUMPSTATE_DEADLINE_SEC,
        bugreport_id,
        allowed_statuses=[STATUS_WRITE_PENDING],
        fail=True)
    if err_msg:
      _fail_program('Dumpstate (bugreport) failed: %s' % err_msg)
    print('\nDumpstate (bugreport) completed (or failed).')

  def _wait_for_bugreport_to_upload(self, bugreport_id):
    """Waits bugreport to be uploaded and returns None if succeeds."""
    print('\nWaiting for the bug report to be uploaded.')
    err_msg = self._wait_for_bugreport_status_to_change_to(
        STATUS_UPLOAD_SUCCESS,
        UPLOAD_DEADLINE_SEC,
        bugreport_id,
        allowed_statuses=[STATUS_UPLOAD_PENDING])
    if err_msg:
      print('Failed to upload: %s' % err_msg)
      return err_msg
    print('\nBugreport was successfully uploaded.')
    return None

  def _extract_important_files(self, local_zippath):
    """Extracts txt, jpg, png and 3gp files from the zip file."""
    files = []
    with zipfile.ZipFile(local_zippath) as zipf:
      for info in zipf.infolist():
        file_ext = info.filename.split('.')[-1]
        if file_ext in ['txt', 'jpg', 'png', '3gp']:
          files.append(
              File(
                  name=info.filename,
                  content=zipf.read(info.filename),
                  size=info.file_size,
                  compress_size=info.compress_size))
    return files

  def _is_image(self, file):
    """Returns True if the file is an image."""
    ext = file.name.split('.')[-1]
    return ext in ['png', 'jpg']

  def _validate_image(self, file):
    if file.compress_size == 0:
      return _red('[Invalid] Image %s is empty.' % file.name)
    return file.name + ' (%d kb)' % (file.compress_size / 1024)

  def _is_audio(self, file):
    """Returns True if the file is an audio."""
    return file.name.endswith('.3gp')

  def _validate_audio(self, file):
    """If valid returns (True, msg), otherwise returns (False, msg)."""
    if file.compress_size == 0:
      return _red('[Invalid] Audio %s is empty' % file.name)
    return file.name + ' (%d kb)' % (file.compress_size / 1024)

  def _is_dumpstate(self, file):
    """Returns True if the file is a dumpstate (bugreport) results."""
    if not file.name.endswith('.txt'):
      return None  # Just ignore.
    content = file.content.decode('ascii', 'ignore')
    return '== dumpstate:' in content

  def _parse_dumpstate(self, file):
    """Parses dumpstate file and returns BuildProperties."""
    properties = {}
    lines = file.content.decode('ascii', 'ignore').split('\n')
    for line in lines:
      match = PROP_LINE_RE.match(line.strip())
      if match:
        prop, value = match.group(1), match.group(2)
        properties[prop] = value
    return BuildProperties(fingerprint=properties['ro.build.fingerprint'])

  def _validate_dumpstate(self, file, build_properties):
    """If valid returns (True, msg), otherwise returns (False, msg)."""
    if file.compress_size < 100 * 1024:  # suspicious if less than 100 kb
      return _red('[Invalid] Suspicious dumpstate: %s, size: %d bytes' %
                  (file.name, file.compress_size))
    if not build_properties.fingerprint:
      return _red('[Invalid] Strange dumpstate without fingerprint: %s' %
                  file.name)
    return file.name + ' (%.2f mb)' % (file.compress_size / 1024.0 / 1024.0)

  def _validate_files(self, files, local_zippath, meta_bugreport):
    """Validates files extracted from zip file and returns validation result.

    Arguments:
      files : List[File] - list of files extracted from bugreport zip file.
      local_zippath : str - bugreport zip file path.
      meta_bugreport : MetaBugReport - a subject bug report.

    Returns:
      List[str] - a validation result that can be printed.
    """
    images = []
    dumpstates = []
    audios = []
    build_properties = BuildProperties(fingerprint='')
    for file in files:
      if self._is_image(file):
        images.append(self._validate_image(file))
      elif self._is_audio(file):
        audios.append(self._validate_audio(file))
      elif self._is_dumpstate(file):
        build_properties = self._parse_dumpstate(file)
        dumpstates.append(self._validate_dumpstate(file, build_properties))

    result = []
    zipfilesize = os.stat(local_zippath).st_size
    result.append('Zip file: %s (%.2f mb)' % (os.path.basename(
        meta_bugreport.filepath), zipfilesize / 1024.0 / 1024.0))
    result.append('Fingerprint: %s\n' % build_properties.fingerprint)
    result.append('Images count: %d ' % len(images))
    for img_validation in images:
      result.append('   - %s' % img_validation)
    result.append('\nAudio count: %d ' % len(audios))
    for audio_validation in audios:
      result.append('   - %s' % audio_validation)
    result.append('\nDumpstate (bugreport) count: %d ' % len(dumpstates))
    for dumpstate_validation in dumpstates:
      result.append('   - %s' % dumpstate_validation)
    return result

  def _write_files_to_data_dir(self, files, data_dir):
    """Writes files to data_dir."""
    for file in files:
      if (not (self._is_image(file) or self._is_audio(file) or
          self._is_dumpstate(file))):
        continue
      with open(os.path.join(data_dir, file.name), 'wb') as wfile:
        wfile.write(file.content)
    print('Files have been written to %s' % data_dir)

  def _process_bugreport(self, meta_bugreport):
    """Checks zip file contents, returns validation results.

    Arguments:
      meta_bugreport : MetaBugReport - a subject bugreport.

    Returns:
      List[str] - validation results.
    """
    print('Processing bugreport id=%s, timestamp=%s' %
          (meta_bugreport.id, meta_bugreport.timestamp))
    tmpdir = tempfile.mkdtemp(prefix='aae-bugreport-', suffix='zip', dir=".")
    zippath = tmpdir + '/bugreport.zip'
    exit_code, stdout_lines = self._device.adb(
        ['pull', meta_bugreport.filepath, zippath])
    if exit_code != 0:
      print('\n'.join(stdout_lines))
      shutil.rmtree(tmpdir, ignore_errors=True)
      _fail_program('Failed to pull bugreport zip file, exit_code=%s' %
                    exit_code)
    print('Zip file saved to %s' % zippath)

    files = self._extract_important_files(zippath)
    results = self._validate_files(files, zippath, meta_bugreport)

    self._write_files_to_data_dir(files, tmpdir)

    return results

  def run(self):
    """Runs BugreportAppTester."""
    self._device.restart_adb_as_root()

    if self._device.pidof('dumpstate'):
      _fail_program('\nFailure. dumpstate binary is already running.')

    self._device.disable_internet()
    self._kill_bugreport_app()
    self._delete_all_bugreports()

    # Start BugReport App; it starts recording audio.
    self._start_bug_report()
    print('\n\n')
    print(_green('************** MANUAL **************'))
    print(
        'Please speak something to the device\'s microphone.\n'
        'After that press *Submit* button and wait until the script finishes.\n'
    )
    time.sleep(META_BUGREPORT_WAIT_TIME_SEC)
    meta_bugreport = self._get_active_bugreport()

    self._wait_for_bugreport_to_complete(meta_bugreport.id)

    check_results = self._process_bugreport(meta_bugreport)

    self._device.enable_internet()

    err_msg = self._wait_for_bugreport_to_upload(meta_bugreport.id)
    if err_msg:
      check_results += [
          _red('\nUpload failed, make sure the device has '
               'Internet: ' + err_msg)
      ]
    else:
      check_results += ['\nUpload succeeded.']

    print('\n\n')
    print(_green('************** FINAL RESULTS *********************'))
    print('%s v%s' % (os.path.basename(__file__), VERSION))

    print('\n'.join(check_results))
    print()
    print('Please verify the contents of files.')


def main():
  parser = argparse.ArgumentParser(description='BugReport App Tester.')
  parser.add_argument(
      '-s', metavar='SERIAL', type=str, help='use device with given serial.')

  args = parser.parse_args()

  device = Device(serialno=args.s)
  BugreportAppTester(device).run()


if __name__ == '__main__':
  main()
