# Setup with Intellij™

### Dependencies


##### 1) Install a JDK (we recommend [AdoptOpenJDK](https://adoptopenjdk.net/))

##### 2) [Get the Groovy SDK](http://groovy-lang.org/)

##### 3) [Get Ant](https://ant.apache.org/)

##### 4) Open Intellij Settings and make sure you also have the Groovy plugin for Intellij
<img src="readmeimages/groovy.png" width="500px"/>

##### 5) Clone the project

`$ git clone https://github.com/stackct/toxic.git`

##### 6) Open the project and go to `File > Project Structure`

##### 7) Under `Platform Settings > SDKs` and add your JDK installation
<img src="readmeimages/sdks.png" width="500px"/>

##### 8) Under `Platform Settings > Global Libraries` add your groovy installation
<img src="readmeimages/libraries.png" width="500px"/>

##### 9) Under `Project Settings > Project` set your JDK version
<!> remember to also set the project language level <!>
<img src="readmeimages/sdk.png" width="500px"/>

##### 10) Go to `Project Settings > Modules > Sources` and right click to mark folders:

- Mark `src` as "Sources"
- Mark `test` as "Tests"
- Mark `resources` as "Resources"

<img src="readmeimages/folders.png" width="500px"/>

##### 11) Under `Project Settings > Modules > Paths` set your output directories as `/gen`
<img src="readmeimages/output.png" width="500px"/>

##### 12) Under `Project Settings > Modules > Dependencies` make sure the correct JDK is selected for the TOXIC module
<img src="readmeimages/modules.png" width="500px"/>

##### 13) Go to the `Paths` tab and set the output path and output directories
