# 3rd party instructions for using series recording feature of Live Channels

## Prerequisites

*   Updated agreement with Google
*   Oreo or patched Nougat

## Nougat

To enable series recording with Nougat you will need the following changes.

### Patch TVProvider

To run in Nougat you must backport the following changes

*   [Filter out non-existing customized columns in
    DB](https://partner-android.googlesource.com/platform/packages/providers/TvProvider/+/142162af889b2c124bb012eea608c6a65eed54bb)
*   [Add TvProvider methods to get and add
    columns](https://partner-android.googlesource.com/platform/packages/providers/TvProvider/+/cda6788ae903513a555fd3e07a5a1c14218c40a2)

### Customisation

Indicate TvProvider is patched by including the following in their TV
customization resource

```
<bool name="tvprovider_allows_column_creation">true</bool>
```

See https://source.android.com/devices/tv/customize-tv-app
