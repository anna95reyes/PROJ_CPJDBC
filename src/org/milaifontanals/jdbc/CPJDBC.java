/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.milaifontanals.jdbc;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import org.milaifontanals.interficie.GestioProjectesException;
import org.milaifontanals.interficie.IGestioProjectes;

/**
 *
 * @author anna9
 */
public class CPJDBC implements IGestioProjectes {
    
    private Connection con;

    public CPJDBC() throws GestioProjectesException {
        this("CPJDBC.properties");
    }

    public CPJDBC(String nomFitxerPropietats) throws GestioProjectesException {
        if (nomFitxerPropietats == null) {
            nomFitxerPropietats = "CPJDBC.properties";
        }
        Properties p = new Properties();
        try {
            p.load(new FileInputStream(nomFitxerPropietats));
        } catch (IOException ex) {
            throw new GestioProjectesException("Error en llegir de fitxer de propietats", ex);
        }
        String url = p.getProperty("url");
        if (url == null || url.length() == 0) {
            throw new GestioProjectesException("Fitxer de propietats " + nomFitxerPropietats + " no inclou propietat \"url\"");
        }
        String user = p.getProperty("user");
        String password = p.getProperty("password");
        String driver = p.getProperty("driver");    // optatiu
        // Si ens passen driver, ens estan dient que l'hem de carregar
        // Si no ens passen driver, no l'hem de carregar (suposat >= JDBC 4.0)
        if (driver != null && driver.length() > 0) {
            try {
                Class.forName(driver).newInstance();
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException ex) {
                throw new GestioProjectesException("Problemes en carregar el driver ", ex);
            }
        }
        try {
            if (user != null && user.length() > 0) {
                con = DriverManager.getConnection(url, user, password);
            } else {
                con = DriverManager.getConnection(url);
            }
        } catch (SQLException ex) {
            throw new GestioProjectesException("Problemes en establir la connexió ", ex);
        }
        try {
            con.setAutoCommit(false);
        } catch (SQLException ex) {
            throw new GestioProjectesException("Problemes en desactivar autocommit ", ex);
        }
    }
    
    @Override
    public void closeCapa() throws GestioProjectesException {
        if (con != null) {
            try {
                con.rollback();
                con.close();
            } catch (SQLException ex) {
                throw new GestioProjectesException("Problemes en tancar la connexió ", ex);
            }
            con = null;
        }
    }

    @Override
    public void commit() throws GestioProjectesException {
        try {
            con.commit();
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en fer commit", ex);
        }
    }

    @Override
    public void rollback() throws GestioProjectesException {
        try {
            con.rollback();
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en fer rollback", ex);
        }
    }
    
    
    
}
