# BlobStore

[![Build Status](https://secure.travis-ci.org/jponge/blobstore.png)](http://travis-ci.org/jponge/blobstore)

A local, non thread-safe implementation of a key / value blobs storage in Java.

This is not mean to be bulletproof: this project purpose is to illustrate
the benefits of using [JBoss Byteman](http://www.jboss.org/byteman/) to
harness unit and functional test suites by injecting faults.

There are many cases where exceptional behaviour is hard to reproduce in tests,
especially when I/O and multiple threads are involved. JBoss Byteman manipulates
application bytecode through event/condition/action rules, and can make it very
easy to inject faults, wait/pause threads and so on.

## Discussion

The `master` branch contains the latest iterations over the code base and
tests suite.

The `without-byteman` branch contains the initial code base that was developed
with traditional unit tests, without fault injection.

By comparing both branches, you can see how introducing faults with Byteman
helped in:

* vastly increasing code coverage, and
* detecting a few bugs.

One may argue that the `without-byteman` branch could still be improved
without Byteman, and that the few bugs fixed afterwards in the `master`
branch could still have been spotted through peer-review.

This is true.

The point is that the `without-byteman` branch still had a reasonable
code coverage to begin with, with the missing spots lying in I/O errors
that would be very hard to introduce through even with object stubs/mocks.

Forcing a filesystem to refuse renaming a file, or forcing it to become
full all of a sudden is definitely non-trivial.

If you have experience with other techniques than leveraging a bytecode
manipulation tool like Byteman then I am very interested in hearing from
you!

## License

Copyright (c) 2012 [Julien Ponge](http://julien.ponge.info/).

    Permission is hereby granted, free of charge, to any person obtaining a copy
    of this software and associated documentation files (the "Software"), to deal
    in the Software without restriction, including without limitation the rights
    to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
    copies of the Software, and to permit persons to whom the Software is
    furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in
    all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
    IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
    FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.  IN NO EVENT SHALL THE
    AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
    LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
    OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
    THE SOFTWARE.

## Building

The JBoss repositories can be boring to configure right with Maven, especially
if you want to avoid putting explicit repository references in your POMs, which
is bad practice anyway.

[Read on this JBoss documentation](https://community.jboss.org/wiki/MavenGettingStarted-Users).

## You can contribute!

Although this project is a use-case rather than a real attempt at making a rock-solid
blob storage engine, feel-free to propose enhancements.

