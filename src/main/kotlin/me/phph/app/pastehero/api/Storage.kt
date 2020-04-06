package me.phph.app.pastehero.api

import java.io.ByteArrayInputStream
import java.sql.*
import javax.imageio.ImageIO

object Storage {
    private const val TABLE_ENTRIES = "entries"

    private var connection: Connection? = null

    init {
        initTable()
    }

    private fun getConnection(): Connection {
        if (connection == null) {
            val homePath = Configuration.getConfiguration(Configuration.CONF_APP_HOME)
            val databaseName = Configuration.getConfiguration(Configuration.CONF_DB_NAME)
            Class.forName("org.sqlite.JDBC")
            connection = DriverManager.getConnection("jdbc:sqlite:${homePath + databaseName}")
            connection?.autoCommit = false
        }
        return connection!!
    }

    private fun initTable() {
        val entryTableSql = ("create table if not exists $TABLE_ENTRIES("
                + "id integer primary key autoincrement,"
                + "type integer,"
                + "data text,"
                + "binary blob,"
                + "update_ts integer"
                + ")")
        var statement: Statement? = null
        try {
            statement = getConnection().createStatement()
            statement.executeUpdate(entryTableSql)
            getConnection().commit()
        } catch (e: Exception) {
            getConnection().rollback()
            e.printStackTrace()
            throw e
        } finally {
            statement?.close()
        }
    }

    fun cleanup() {
        getConnection().close()
    }

    fun count(): Int {
        var statement: Statement? = null
        var resultSet: ResultSet? = null
        try {
            statement = getConnection().createStatement()
            resultSet = statement.executeQuery("select count(id) as c from entries")
            while (resultSet.next()) {
                return resultSet.getInt("c")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement?.close()
            resultSet?.close()
        }
        return 0;
    }


    fun saveEntry(entry: Entry) {
        var statement1: PreparedStatement? = null
        var statement2: Statement? = null
        var resultSet: ResultSet? = null
        try {
            val sql = "insert into $TABLE_ENTRIES values(NULL,?,?,?,?)"
            statement1 = getConnection().prepareStatement(sql)
            statement1.setString(1, entry.type.toString())
            if (entry.type == EntryType.STRING) {
                statement1.setString(2, entry.value)
                statement1.setString(3, null)
            } else if (entry.type == EntryType.IMAGE) {
                statement1.setString(2, null)
                statement1.setBytes(3, readImage(entry.image!!))
            }
            val updateTs = System.currentTimeMillis().toInt()
            statement1.setInt(4, updateTs)
            statement1.executeUpdate()
            getConnection().commit()

            val queryIdSql = "select seq from sqlite_sequence where name='$TABLE_ENTRIES'"
            statement2 = getConnection().createStatement()
            resultSet = statement2.executeQuery(queryIdSql)
            while (resultSet.next()) {
                val id = resultSet.getInt(1)
                entry.id = id
            }
            entry.updateTs = updateTs
        } catch (e: Exception) {
            e.printStackTrace()
            getConnection().rollback()
        } finally {
            statement1?.close()
            statement2?.close()
            resultSet?.close()
        }
    }

    fun updateEntry(entry: Entry) {
        var statement: PreparedStatement? = null
        try {
            val sql = "update $TABLE_ENTRIES set type=?, data=?,binary=?,update_ts=? where id=?"
            statement = getConnection().prepareStatement(sql)
            statement.setString(1, entry.type.toString())
            if (entry.type == EntryType.STRING) {
                statement.setString(2, entry.value)
                statement.setString(3, null)
            } else if (entry.type == EntryType.IMAGE) {
                statement.setString(2, null)
                statement.setBytes(3, readImage(entry.image!!))
            }
            val updateTs = System.currentTimeMillis().toInt()
            statement.setInt(4, updateTs)
            statement.setInt(5, entry.id)
            statement.executeUpdate()
            getConnection().commit()
            entry.updateTs = updateTs
        } catch (e: Exception) {
            e.printStackTrace()
            getConnection().rollback()
        } finally {
            statement?.close()
        }
    }

    fun listEntryById(id: Int): Entry {
        val sql = "select * from $TABLE_ENTRIES where id=$id"
        var statement: Statement? = null
        var resultSet: ResultSet? = null
        try {
            statement = getConnection().createStatement()
            resultSet = statement.executeQuery(sql)
            while (resultSet.next()) {
                return Entry(resultSet.getInt(1),
                        EntryType.valueOf(resultSet.getString(2)),
                        resultSet.getString(3) ?: "",
                        resultSet.getBytes(4)?.let { ImageIO.read(ByteArrayInputStream(it)) },
                        resultSet.getInt(5))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement?.close()
            resultSet?.close()
        }
        return Entry(-1)
    }

    fun listRecentEntries(count: Int): List<Entry> {
        val sql = ("select * from $TABLE_ENTRIES"
                + " order by update_ts desc limit $count")
        val retList = mutableListOf<Entry>()
        var statement: Statement? = null
        var resultSet: ResultSet? = null
        try {
            statement = getConnection().createStatement()
            resultSet = statement.executeQuery(sql)
            while (resultSet.next()) {
                retList.add(Entry(resultSet.getInt(1),
                        EntryType.valueOf(resultSet.getString(2)),
                        resultSet.getString(3) ?: "",
                        resultSet.getBytes(4)?.let { ImageIO.read(ByteArrayInputStream(it)) },
                        resultSet.getInt(5)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement?.close()
            resultSet?.close()
        }
        return retList.toList()
    }

    fun listEntries(countPerPage: Int, pageNumber: Int): List<Entry> {
        val start = countPerPage * (pageNumber - 1)
        val end = start + countPerPage

        val sql = ("select * from $TABLE_ENTRIES"
                + " order by update_ts desc limit $start,$end")
        val retList = mutableListOf<Entry>()
        var statement: Statement? = null
        var resultSet: ResultSet? = null
        try {
            statement = getConnection().createStatement()
            resultSet = statement.executeQuery(sql)
            while (resultSet.next()) {
                retList.add(Entry(resultSet.getInt(1),
                        EntryType.valueOf(resultSet.getString(2)),
                        resultSet.getString(3) ?: "",
                        resultSet.getBytes(4)?.let { ImageIO.read(ByteArrayInputStream(it)) },
                        resultSet.getInt(5)))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            statement?.close()
            resultSet?.close()
        }
        return retList.toList()
    }

    fun deleteEntry(id: Int) {
        var statement: Statement? = null
        try {
            statement = getConnection().createStatement()
            statement.executeUpdate("delete from $TABLE_ENTRIES where id=$id")
            getConnection().commit()
        } catch (e: Exception) {
            e.printStackTrace()
            getConnection().rollback()
        } finally {
            statement?.close()
        }
    }

}
