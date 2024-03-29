Simple DHT

Introduction

In this assignment, you will design a simple DHT based on Chord. Although the design is based on Chord, it is a simplified version of Chord; you do not need to implement finger tables and finger-based routing; you also do not need to handle node leaves/failures.Therefore, there are three things you need to implement: 1) ID space partitioning/re-partitioning, 2) Ring-based routing, and 3) Node joins.
Just like the previous assignment, your app should have an activity and a content provider. However, the main activity should be used for testing only and should not implement any DHT functionality. The content provider should implement all DHT functionalities and support insert and query operations. Thus, if you run multiple instances of your app, all content provider instances should form a Chord ring and serve insert/query requests in a distributed fashion according to the Chord protocol.
References

Before we discuss the requirements of this assignment, here are two references for the Chord design:
Lecture slides on Chord: http://www.cse.buffalo.edu/~stevko/courses/cse486/spring14/lectures/13-dht.pptx
Chord paper: http://conferences.sigcomm.org/sigcomm/2001/p12-stoica.pdf
The lecture slides give an overview, but do not discuss Chord in detail, so it should be a good reference to get an overall idea. The paper presents pseudo code for implementing Chord, so it should be a good reference for actual implementation.
Note

It is important to remember that this assignment does not require you to implement everything about Chord. Mainly, there are three things you do not need to consider from the Chord paper.
Fingers and finger-based routing (i.e., Section 4.3 & any discussion about fingers in Section 4.4)
Concurrent node joins (i.e., Section 5)
Node leaves/failures (i.e., Section 5)
We will discuss this more in “Step 2: Writing a Content Provider” below.
Step 0: Importing the project template

Just like the previous assignment, we have a project template you can import to Eclipse.
Download the project template zip file to a directory.
Import it to your Eclipse workspace.
Open Eclipse.
Go to “File” -> “Import”
Select “General/Existing Projects into Workspace” (Caution: this is not “Android/Existing Android Code into Workspace”).
In the next screen (which should be “Import Projects”), do the following:
Choose “Select archive file:” and select the project template zip file that you downloaded.
Click “Finish.”
At this point, the project template should have been imported to your workspace.
You might get an error saying “Android requires compiler compliance level...” If so, right click on “SimpleDht” from the Package Explorer, choose “Android Tools” -> “Fix Project Properties” which will fix the error.
You might also get an error about android-support-v4.jar. If so, right click on “SimpleDht” from the Package Explorer, choose “Properties” -> “Java Build Path” -> “Libraries” and either fix the android-support-v4.jar’s path or replace it with your SDK’s correct android-support-v4.jar.
Try running it on an AVD and verify that it’s working.
Use the project template for implementing all the components for this assignment.
The template has the package name of “edu.buffalo.cse.cse486586.simpledht“. Please do not change this.
The template also defines a content provider authority and class. Please use it to implement your Chord functionalities.
We will use SHA-1 as our hash function to generate keys. The following code snippet takes a string and generates a SHA-1 hash as a hexadecimal string. Please use it to generate your keys. The template already has the code, so you just need to use it at appropriate places. Given two keys, you can use the standard lexicographical string comparison to determine which one is greater.
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
private String genHash(String input) throws NoSuchAlgorithmException {
MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
byte[] sha1Hash = sha1.digest(input.getBytes());
Formatter formatter = new Formatter();
for (byte b : sha1Hash) {
formatter.format("%02x", b);
}
return formatter.toString();
}
Step 1: Writing the Content Provider

First of all, your app should have a content provider. This content provider should implement all DHT functionalities. For example, it should create server and client threads (if this is what you decide to implement), open sockets, and respond to incoming requests; it should also implement a simplified version of the Chord routing protocol; lastly, it should also handle node joins. The following are the requirements for your content provider:
We will test your app with any number of instances up to 5 instances.
The content provider should implement all DHT functionalities. This includes all communication as well as mechanisms to handle insert/query requests and node joins.
Each content provider instance should have a node id derived from its emulator port. This node id should be obtained by applying the above hash function (i.e., genHash()) to the emulator port. For example, the node id of the content provider instance running on emulator-5554 should be, node_id = genHash(“5554”). This is necessary to find the correct position of each node in the Chord ring.
Your content provider should implement insert(), query(), and delete(). The basic interface definition is the same as the previous assignment, which allows a client app to insert arbitrary <”key”, “value”> pairs where both the key and the value are strings.
For delete(URI uri, String selection, String[] selectionArgs), you only need to use use the first two parameters, uri & selection.  This is similar to what you need to do with query().
However, please keep in mind that this “key” should be hashed by the above genHash() before getting inserted to your DHT in order to find the correct position in the Chord ring.
For your query() and delete(), you need to recognize two special strings for the selection parameter.
If “*” (a string with a single character *) is given as the selection parameter to query(), then you need to return all <key, value> pairs stored in your entire DHT.
Similarly, if “*” is given as the selection parameter to delete(), then you need to delete all <key, value> pairs stored in your entire DHT.
If “@” (a string with a single character @) is given as the selection parameter to query() on an AVD, then you need to return all <key, value> pairs stored in your local partition of the node, i.e., all <key, value> pairs stored locally in the AVD on which you run query().
Similarly, if “@” is given as the selection parameter to delete() on an AVD, then you need to delete all <key, value> pairs stored in your local partition of the node, i.e., all <key, value> pairs stored locally in the AVD on which you run delete().
An app that uses your content provider can give arbitrary <key, value> pairs, e.g., <”I want to”, “store this”>; then your content provider should hash the key via genHash(), e.g., genHash(“I want to”), get the correct position in the Chord ring based on the hash value, and store <”I want to”, “store this”> in the appropriate node.
Your content provider should implement ring-based routing. Following the design of Chord, your content provider should maintain predecessor and successor pointers and forward each request to its successor until the request arrives at the correct node. Once the correct node receives the request, it should process it and return the result (directly or recursively) to the original content provider instance that first received the request.
Your content provider do not need to maintain finger tables and implement finger-based routing. This is not required.
As with the previous assignment, we will fix all the port numbers (see below). This means that you can use the port numbers (11108, 11112, 11116, 11120, & 11124) as your successor and predecessor pointers.
Your content provider should handle new node joins. For this, you need to have the first emulator instance (i.e., emulator-5554) receive all new node join requests. Your implementation should not choose a random node to do that. Upon completing a new node join request, affected nodes should have updated their predecessor and successor pointers correctly.
Your content provider do not need to handle concurrent node joins. You can assume that a node join will only happen once the system completely processes the previous join.
Your content provider do not need to handle insert/query requests while a node is joining. You can assume that insert/query requests will be issued only with a stable system.
Your content provider do not need to handle node leaves/failures. This is not required.
We have fixed the ports & sockets.
Your app should open one server socket that listens on 10000.
You need to use run_avd.py and set_redir.py to set up the testing environment.
The grading will use 5 AVDs. The redirection ports are 11108, 11112, 11116, 11120, and 11124.
You should just hard-code the above 5 ports and use them to set up connections.
Please use the code snippet provided in PA1 on how to determine your local AVD.
emulator-5554: “5554”
emulator-5556: “5556”
emulator-5558: “5558”
emulator-5560: “5560”
emulator-5562: “5562”
Your content provider’s URI should be: “content://edu.buffalo.cse.cse486586.simpledht.provider”, which means that any app should be able to access your content provider using that URI. Your content provider does not need to match/support any other URI pattern.
As with the previous assignment, Your provider should have two columns.
The first column should be named as “key” (an all lowercase string without the quotation marks). This column is used to store all keys.
The second column should be named as “value” (an all lowercase string without the quotation marks). This column is used to store all values.
All keys and values that your provider stores should use the string data type.
Note that your content provider should only store the <key, value> pairs local to its own partition.
Step 2: Writing the Main Activity

The template has an activity used for your own testing and debugging. It has three buttons, one button that displays “Test”, one button that displays “LDump” and another button that displays “GDump.” As with the previous assignment, “Test” button is already implemented (it’s the same as “PTest” from the last assignment). You can implement the other two buttons to further test your DHT.
LDump
When touched, this button should dump and display all the <key, value> pairs stored in your local partition of the node.
This means that this button can give “@” as the selection parameter to query().
GDump
When touched, this button should dump and display all the <key, value> pairs stored in your whole DHT. Thus, LDump button is for local dump, and this button (GDump) is for global dump of the entire <key, value> pairs.
This means that this button can give “*” as the selection parameter to query().
Testing

We have testing programs to help you see how your code does with our grading criteria. If you find any rough edge with the testing programs, please report it on Piazza so the teaching staff can fix it. The instructions are the following:
Download a testing program for your platform. If your platform does not run it, please report it on Piazza.
Windows: We’ve tested it on 32- and 64-bit Windows 8.
Linux: We’ve tested it on 32- and 64-bit Ubuntu 12.04.
OS X: We’ve tested it on 32- and 64-bit OS X 10.9 Mavericks.
Before you run the program, please make sure that you are running five AVDs. python run_avd.py 5 will do it.
Run the testing program from the command line.
On your terminal, it will give you your partial and final score, and in some cases, problems that the testing program finds.
Submission

We use the CSE submit script. You need to use either “submit_cse486” or “submit_cse586”, depending on your registration status. If you haven’t used it, the instructions on how to use it is here: https://wiki.cse.buffalo.edu/services/content/submit-script
You need to submit one file described below. Once again, you must follow everything below exactly. Otherwise, you will get no point on this assignment.
Your entire Eclipse project source code tree zipped up in .zip: The name should be SimpleDht.zip. Please do not change the name. To do this, please do the following
Open Eclipse.
Go to “File” -> “Export”.
Select “General -> Archive File”.
Select your project. Make sure that you include all the files and check “Save in zip format”.
Please do not use any other compression tool other than zip, i.e., no 7-Zip, no RAR, etc.
Deadline: 4/11/14 (Friday) 1:59:59pm

The deadline is firm; if your timestamp is 2pm, it is a late submission.
Grading

This assignment is 15% of your final grade. The breakdown for this assignment is:
1% if local insert/query/delete operations work correctly with 1 AVD.
Additional 3% if the insert operation works correctly with static/stable membership of 5 AVDs.
Additional 3% if the query operation works correctly with static/stable membership of 5 AVDs.
Additional 3% if the insert operation works correctly with 1 - 5 AVDs.
Additional 3% if the query operation works correctly with 1 - 5 AVDs.
Additional 2% if the delete operation works correctly with 1 - 5 AVDs.
