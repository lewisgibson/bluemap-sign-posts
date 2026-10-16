/**
 * The JDBC store: one store class over a connection source per database (a SQLite file, or a pooled
 * PostgreSQL or MySQL server), the SQL dialects, the numbered schema migrations, and the single
 * thread that runs writes off the server thread. Nothing in this package imports a server or a map
 * type.
 */
package com.lewisgibson.bluemapsignposts.storage;
