# datascript-firebase

An example of using datascript, firebase, and reagent for a realtime collaborative web app


## How to run

Setup a new firebase project
add your api key and databaseURL to init! fn in firebase.cljs file
Make sure you have your database rules set to public read and write (or setup firebase auth and corresponding rules)

Then run

`npm install`


`shadow-cljs watch app`

navigate to localhost:8080


## Warning

This repo works for the initial setup, but there are more things you'll have to do to make this work in production depending on your needs.

Feel free to contact me if you have any questions
