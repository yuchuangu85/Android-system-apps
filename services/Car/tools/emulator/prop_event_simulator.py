import vhal_consts_2_0 as c
from vhal_emulator import Vhal

import argparse
import json
import sys
import array

vhal_types = c.vhal_types_2_0

def propType(con):
    return getattr(c,con)

def parseVal(val, valType):
    if valType in vhal_types.TYPE_STRING:
        return str(val)
    elif valType in vhal_types.TYPE_INT32:
        return int(val)
    elif valType in vhal_types.TYPE_INT32S:
        return map(int, val.split(','))
    elif valType in vhal_types.TYPE_INT64:
        return long(val)
    elif valType in vhal_types.TYPE_INT64S:
        return map(long, val.split(','))
    elif valType in vhal_types.TYPE_FLOAT:
        return float(val)
    elif valType in vhal_types.TYPE_FLOATS:
        return map(float, val.split(','))
    elif valType in vhal_types.TYPE_MIXED:
        print val
        return json.loads(val)
    else:
        raise ValueError('Property value type not recognized:', valType)
        return

def main():
    parser = argparse.ArgumentParser(
         description='Execute vehicle simulation to simulate actual car sceanrios.')
    parser.add_argument(
        '-s',
        type=str,
        action='store',
        dest='device',
        default=None,
        help='Device serial number. Optional')
    parser.add_argument(
        '--property',
        type=propType,
        default=c.VEHICLEPROPERTY_EV_CHARGE_PORT_OPEN,
        help='Property name from vhal_consts_2_0.py, e.g. VEHICLEPROPERTY_EV_CHARGE_PORT_OPEN')
    parser.add_argument(
       '--area',
        default=0,
        type=int,
        help='Area id for the property, "0" for global')
    parser.add_argument(
       '--value',
        type=str,
        help='Property value. If the value is MIXED type, you should provide the JSON string \
              of the value, e.g. \'{"int32_values": [0, 291504647], "int64_values": [1000000], \
              "float_values": [0.0, 30, 0.1]}\' which is for fake data generation controlling \
              property in default VHAL. If the value is array, use comma to split values')
    args = parser.parse_args()
    if not args.property:
      print 'Project is required. Use --help to see options.'
      sys.exit(1)

    executeCommand(args);

def executeCommand(args):
    # Create an instance of vhal class.  Need to pass the vhal_types constants.
    v = Vhal(c.vhal_types_2_0, args.device);

    # Get the property config (if desired)
    # property = args.property;
    print args.property
    #i = c.VEHICLEPROPERTY_EV_CHARGE_PORT_OPEN
    v.getConfig(args.property);

    # Get the response message to getConfig()
    reply = v.rxMsg();
    print(reply);

    value = parseVal(args.value, reply.config[0].value_type)
    v.setProperty(args.property, args.area, value);

    # Get the response message to setProperty()
    reply = v.rxMsg();
    print(reply);

if __name__=="__main__":
    main()