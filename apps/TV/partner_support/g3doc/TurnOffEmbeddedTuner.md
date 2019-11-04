# 3rd party instructions turning off the embedded tuner in Live Channels

Partners that have a built in tuner should provide a TV Input like
SampleDvbTuner. When partners provide their own tuner they MUST turn of the
embedded tuner in Live Channels.

### Customisation

Indicate Live Channels should not use it's embedded tuner implementation.

```
<bool name="turn_off_embedded_tuner">true</bool>
```

See https://source.android.com/devices/tv/customize-tv-app
