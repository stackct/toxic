# Setup with Intellij™

##### 1) Install a JDK (we recommend [AdoptOpenJDK](https://adoptopenjdk.net/))
    At the time of writing, JDK 8 is the only version supported by TOXIC


    <!> Linux: Make sure to add Java to your $PATH in `~/.profile` <!>

    It must be listed in the `~/.profile` in order for Ant to successfully run integration tests.

##### 2) [Get the Groovy SDK](http://groovy-lang.org/)

##### 3) [Get Ant](https://ant.apache.org/)

##### 4) Open Intellij Settings and make sure you also have the Groovy plugin for Intellij

##### 5) Clone the project

`$ git clone https://github.com/stackct/toxic.git`

##### 6) Open the project and go to `File > Project Structure`

##### 7) Under `Platform Settings > SDKs` and add your JDK installation

##### 8) Under `Platform Settings > Global Libraries` add your groovy installation

##### 9) Under `Project Settings > Project` set your JDK version
<!> remember to also set the project language level <!>

##### 10) Go to `Project Settings > Modules > Sources` and right click to mark folders:

- Mark `src` as "Sources"
- Mark `test` as "Tests"
- Mark `resources` as "Resources"

##### 11) Under `Project Settings > Modules > Paths` set your output directories.

Select "Use Module Compile Output Path"

Set Output Path to `/gen`

Set Test Output Path to `/gen`

##### 12) Under `Project Settings > Modules > Dependencies`.
Make sure the correct JDK is selected for the TOXIC module.

Click the + button and add your Groovy install

Click the + again to add the `toxic/lib` folder

##### 13) Exit the Project Structure window. In the main Intellij window, go to `View > Tool Windows > Ant`

Press the + button and add the build.xml file.

Execute the `test` task

Open the `Messages` tab and view the logs as tests run.

##### 14) Finally...
    
Open up any unit test file and verify that Intellij is able to recognize everything.

If Intellij is not recognizing class names, it could be that it does not have dependencies setup correctly. Review step 12.

