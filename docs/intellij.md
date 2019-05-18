# Setup with Intellij™

### Prerequisites

---

#### 1) [Get the Groovy SDK](http://groovy-lang.org/)

#### 2) [Get Ant and add it to your terminal path](https://ant.apache.org/)

#### 3) Go to `File > Project Structure` and add Groovy to your Intellij Global Libraries

<img src="readmeimages/libraries.png" width="500px"/>

#### 4) Make sure you also have the Groovy plugin for Intellij

<img src="readmeimages/plugins.png" width="400px"/>

#### 5) Clone the project

`$ git clone https://github.com/stackct/toxic.git`

### Setup for development

---

Open the project and go to `File > Project Structure`

#### 1) Set your Java SDK

<img src="readmeimages/sdk.png" width="400px"/>

#### 2) Go to `Modules > Sources` and right click to mark folders:

- Mark `src` as "Sources"
- Mark `test` as "Tests"
- Mark `resources` as "Resources"

<img src="readmeimages/set_sources.png" width="500px"/>

#### 3) VERY IMPORTANT! Be sure to click `Apply`

#### 4) Go to the `Paths` tab and set the output path and output directories

<img src="readmeimages/paths.png" width="500px"/>


#### 5) Last step, run `ant` in terminal to build TOXIC!
