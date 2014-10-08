Prerequisites
-------------
- Git 2.0+
- Java 6+ (8+ provides significantly better performance)
- SBT 0.13+

Recommended IDE - IntelliJ IDEA 13+ with the Scala Plugin installed


Getting Started
---------------
1.  Clone the git repo (git clone https://github.com/copygrinder/copygrinder.git)
2.  Run SBT in the root of the cloned directory from from a terminal
3.  Run "gen-idea" (without quotes).  This will create the .idea folder.
4.  Open the cloned root directory in IDEA.  If you open the Build.scala file, you will be prompted to
    import the SBT project.  It is recommended that you DO NOT import the SBT project (choose ignore).
5.  You can now run the project from SBT or by creating a new Application Run configuration pointing to
    the main class org.copygrinder.impure.system.Boot
6.  If you'd like the "problems" view which shows any compilation issues on-the-fly, turn on
    Preferences > Compiler > Make Project Automatically


Configuration
-------------
If you wish to change the default settings (such as port number), copy the file named copygrinder-sample.conf to
copygrinder.conf in the project root directory.


SBT Commands
------------
- reStart    - Starts copygrinder
- reStop     - Stops copygrinder
- ~reStart   - Starts the copygrinder server and automatically restarts it upon code changes
- test       - Runs the unit tests found under src/test/
- it:test    - Runs the functional & integration tests under src/it/
- check      - Runs scalastyle and tests with code coverage.  Highly recommended before pushing commits.
- ~testQuick - Automatically runs only the unit tests for source files that have changed.  Great for TDD.


Pure vs Impure
--------------
Classes in Copygrinder are split into 2 high-level packages: pure and impure.  The essence of pure functions is they
return the same result for the same input without side effects.  Pure code is more testable, predictable, cacheable, and
trivial to use in multi-threaded code.  Thus, pure code is preferable to impure where possible.

A function is consider pure if does not:

- make calls to impure functions.
- perform IO such as reading or writing to the file system or performing network operations.  IO that is
  inconsequential, such as logging is allowed.
- get the current time.
- read/write mutable properties on its object.  A property is considered mutable if it is a var or is a val but not
  deeply-immutable.
- read/write from mutable parameters
- read/write from mutable static properties / singleton objects
- spwan threads which call semi-pure functions.  Semi-pure objects are pure objects that contain mutable state.  A
  semi-pure function is a pure function with exception that it may read/write from mutable properties of its object.
  Thus, these functions are effectively pure in a single-threaded environment, and hence why they are not allowed to be
  multi-threaded.  An example of a semi-pure object is an Array.
- make calls to semi-pure objects that are not fresh.  A variable is considered fresh if the current thread is the only
  thread that is able to make reference to it.  This means a semi-pure object created within a pure function is fresh.
  However, a semi-pure object returned from a pure function is no longer fresh if impure code is a potentially caller.