# solr-loader2

A Clojure tool  to create solr indexes from relational data in databases. This tool can be used as an alternative to the Data Import Handler (DIH) provided in the solr distribution. The main advantage of using this tool instead of DIH is performance and simple configuration.  

## Usage

From the command line run

    java -jar solr-loader2-standalone.jar --upload <config-file>
    
### Configuration
The configuration is specified in an EDN file using a native Clojure format. Database connection parameters and entities for indexing and their SQL to extract the entities are specified in the configuration file. Please see the sample configuration file for details.

### Requirements

 1. Java 7 is required for this tool
 2. Solr 4.x is required
 3. Oracle 10 or above (support for other databases coming soon)

## Design
The tool is designed to load large data sets efficiently. It does so by using a combination of multiple threads and asynchronous processing. 
### Design Principles
 1. Do not read the full data set at any time from the database. Read only the minimum data required to process the document record
 2. Use concurrency by processing data in threads and tasks that can be run in parallel
 3. All blocking IO should be processed concurrently on a separate thread pool
 4. All non-blocking IO and processing tasks should be processed concurrently in separate thread pools or asynchronous channels
 5. Simplify configuration to make the tool easy to use

Java concurrency classes are used for blocking IO tasks and clojure core.async functions are used for non-blocking CPU tasks. Both io and cpu tasks are run concurrently fully utilizing the cores on the machine.
**Other notes:**
 1. core.async thread macro is not used for blocking io as io threads need to be bounded. Once can write pure clojure functions to provide a thread pool for blocking io tasks but this functionality is already provided in the java.util.concurrency package.
 2. The (go (>! ...)) macro is not used for synchronizing execution of blocking and non-blocking tasks since the go macro synchronization only works with other go blocks and blocking io tasks do not run as go blocks. Instead semaphores are used to synchronize execution of blocking and non-blocking tasks running concurrently.
### Java concurrency
 1. A thread pool is used to execute blocking io tasks to limit resource usage instead of creating a new thread per task.
 2. Semaphores are used to control the number of records that can be processed at any given time. 

### Clojure concurrency

 1.  Clojure processing mostly involves transforming SQL data records to Solr document update records. Since the processing is CPU bound, core.async channels are used to process data concurrently.
 2. To upload data to solr, non-blocking io with httpkit and core.async channels are used.

## License

Copyright © 2014 Edward Sumitra

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
