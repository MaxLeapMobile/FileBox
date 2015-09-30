# FileBox 

An Android File Sync Library with MaxLeap. Both client and server are available. Easy to build your own "Dropbox" with it.

[![Building Status](https://travis-ci.org/MaxLeapMobile/FileBox.svg?branch=master)](https://travis-ci.org/MaxLeapMobile/FileBox)

## How to Use

1. Git clone the project or download the zip
2. Run the Example with Android Studio
3. Edit the Example or create your own app with FileBox
 
BTW, you can create your own MaxLeap Account in the [website](https://leap.as) and use your own Appid and APIKey. More example about MaxLeap could visit [this](https://github.com/LeapCloud?utf8=%E2%9C%93&query=demo).

## Usage

You can do your own "Dropbox" with there functions:

* `setSyncRoot(String syncRoot)` Set the local folder as the sync root folder.
* `addFile(File src, File target)` Add a file into FileBox. It will be sync automatically. You can also `move` / `copy` / `rename` / `delete` files in FileBox with SyncManager functions.
* `startSync()` Sync all files with cloud storage manually.
 
![Process](https://raw.githubusercontent.com/MaxLeapMobile/FileBox/master/others/process.png)

## Using the library?

* [VaultX Private Album](https://play.google.com/store/apps/details?id=com.ilegendsoft.jupiter)

If you're using this library in one of your projects just [send me a email](mailto:support@leap.as) and I'll add your project to the list.

## Contribution

Pull requests are welcome! If you have a bug to report, a feature to request or have other questions, [file an issue](https://github.com/MaxLeapMobile/FileBox/issues). I'll try to answer asap.

## License

	Copyright 2015 MaxLeapMobile

	Licensed under the GNU GENERAL PUBLIC LICENSE Version 3 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

	http://www.gnu.org/licenses/gpl-3.0.en.html

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
