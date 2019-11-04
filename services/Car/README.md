Native (C++) code format is required to be compatible with .clang-format file. Run

```
git clang-format --style=file --extension='h,cpp,cc' HEAD~
```

Note that clang-format is *not* desirable for Android java files. Therefore
the  command line above is limited to specific extensions.

