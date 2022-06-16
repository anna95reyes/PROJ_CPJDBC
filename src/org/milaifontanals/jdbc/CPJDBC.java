/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.milaifontanals.jdbc;

import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.milaifontanals.interficie.GestioProjectesException;
import org.milaifontanals.interficie.IGestioProjectes;
import org.milaifontanals.model.Entrada;
import org.milaifontanals.model.Estat;
import org.milaifontanals.model.Projecte;
import org.milaifontanals.model.Rol;
import org.milaifontanals.model.Tasca;
import org.milaifontanals.model.Usuari;

/**
 *
 * @author anna9
 */
public class CPJDBC implements IGestioProjectes {
    
    private Connection con;
    private HashMap<String, String> hmLogin = new HashMap<String, String>();
    private HashMap<Integer, Projecte> hmProjectes = new HashMap();
    private HashMap<Integer, Tasca> hmTasquesAssignades = new HashMap();
    private HashMap<Integer, Estat> hmEstats = new HashMap();
    private HashMap<Integer, Usuari> hmUsuaris = new HashMap();

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

    @Override
    public String hashMD5(String input) throws GestioProjectesException {
        try {
  
            // Static getInstance method is called with hashing MD5
            MessageDigest md = MessageDigest.getInstance("MD5");
  
            // digest() method is called to calculate message digest
            //  of an input digest() return array of byte
            byte[] messageDigest = md.digest(input.getBytes());
  
            // Convert byte array into signum representation
            BigInteger no = new BigInteger(1, messageDigest);
  
            // Convert message digest into hex value
            String hashtext = no.toString(16);
            while (hashtext.length() < 32) {
                hashtext = "0" + hashtext;
            }
            return hashtext;
        } 
  
        // For specifying wrong message digest algorithms
        catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
    
    @Override
    public String Login(String login, String password) throws GestioProjectesException {
        
        String token = hmLogin.get(login);
        if (token != null){
            return token;
        }
        
        try {
            String consulta = "select usu_id, usu_login, usu_password_hash from usuari where usu_login = ? and usu_password_hash = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setString(1, login);
            pst.setString(2, password);
            
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer usu_id = rs.getInt("usu_id");
                String usu_login = rs.getString("usu_login");
                String usu_password_hash = rs.getString("usu_password_hash");
                token = getToken();
                hmLogin.put(login, token);
                System.out.println("login: " + usu_login + " - contrasenya: " + usu_password_hash);
                return token;
            }
            return null;
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    public String getToken() {
        return UUID.randomUUID().toString();
    }
    
    
    @Override
    public List<Projecte> getLlistaProjectes(String sessionId) throws GestioProjectesException {
        if (sessionId == null) return null;
        
        String loginUsuari = buscarUsuari(sessionId);
        if (loginUsuari == null) return null;
        
        try {
            String consulta = "select proj_id, proj_nom, proj_descripcio, usu_cap_projecte from projecte";
            PreparedStatement pst = con.prepareStatement(consulta);
            ResultSet rs = pst.executeQuery();
            
            List<Projecte> projectes = new ArrayList<Projecte>();
            
            while (rs.next()){
                Integer proj_id = rs.getInt("proj_id");
                String proj_nom = rs.getString("proj_nom");
                String proj_descripcio = rs.getString("proj_descripcio");
                Integer usu_cap_projecte = rs.getInt("usu_cap_projecte");
                projectes.add(new Projecte (proj_id, proj_nom, proj_descripcio, getUsuari(usu_cap_projecte)));
            }
            
            for (Projecte proj: projectes) {
                hmProjectes.put(proj.getId(), proj);
            }
            return projectes;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public List<Tasca> getLlistaTasquesAssignades(String sessionId, Integer idEstat, Integer idProjecte) throws GestioProjectesException {
        if (sessionId == null) return null;
        
        String loginUsuari = buscarUsuari(sessionId);
        if (loginUsuari == null) return null;
        
        try {
            String consulta = "select tasc_id, tasc_data_creacio, tasc_nom, tasc_descripcio, tasc_data_limit, proj_id, usu_creada_per, usu_assignada_a, stat_id from tasca where stat_id = ? and proj_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, idEstat);
            pst.setInt(2, idProjecte);
            ResultSet rs = pst.executeQuery();
            
            List<Tasca> tasquesAssignades = new ArrayList<Tasca>();
            
            while (rs.next()){
                Integer tasc_id = rs.getInt("tasc_id");
                Date tasc_data_creacio = rs.getDate("tasc_data_creacio");
                String tasc_nom = rs.getString("tasc_nom");
                String tasc_descripcio = rs.getString("tasc_descripcio");
                Date tasc_data_limit = rs.getDate("tasc_data_limit");
                Integer proj_id = rs.getInt("proj_id");
                Integer usu_creada_per = rs.getInt("usu_creada_per");
                Integer usu_assignada_a = rs.getInt("usu_assignada_a");
                Integer stat_id = rs.getInt("usu_assignada_a");
                
                tasquesAssignades.add(new Tasca(tasc_id, tasc_data_creacio, tasc_nom, tasc_descripcio, tasc_data_limit, 
                           getUsuari(usu_creada_per), getUsuari(usu_assignada_a), getEstat(stat_id)));
            }
            
            for (Tasca tasc: tasquesAssignades) {
                hmTasquesAssignades.put(tasc.getId(), tasc);
            }
            return tasquesAssignades;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    private String buscarUsuari(String sessionId) {
        Set<String> keys = hmLogin.keySet();
        
        for (String key : keys) {
            if (sessionId.equals(hmLogin.get(key))){
                return key;
            }
        }
        return null;
    }
    
    
    
    @Override
    public Usuari getUsuari(int id) throws GestioProjectesException {
        try {
            String consulta = "select usu_id, usu_nom, usu_cognom_1, usu_cognom_2, usu_data_naixement, usu_login, usu_password_hash from usuari where usu_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, id);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer usu_id = rs.getInt("usu_id");
                String usu_nom = rs.getString("usu_nom");
                String usu_cognom_1 = rs.getString("usu_cognom_1");
                String usu_cognom_2 = rs.getString("usu_cognom_2");
                Date usu_data_naixement = rs.getDate("usu_data_naixement");
                String usu_login = rs.getString("usu_login");
                String usu_password_hash = rs.getString("usu_password_hash");
                
                return new Usuari(usu_id, usu_nom, usu_cognom_1, usu_cognom_2, usu_data_naixement, usu_login, usu_password_hash);
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
        
    }
    
    @Override
    public Estat getEstat(int id) throws GestioProjectesException {
        try {
            String consulta = "select stat_id, stat_nom from estat where stat_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, id);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer stat_id = rs.getInt("stat_id");
                String stat_nom = rs.getString("stat_nom");
                
                return new Estat(stat_id, stat_nom);
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public Projecte getProjecte(int id) throws GestioProjectesException {
        try {
            String consulta = "select proj_id, proj_nom, proj_descripcio, usu_cap_projecte from projecte where proj_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, id);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer proj_id = rs.getInt("proj_id");
                String proj_nom = rs.getString("proj_nom");
                String proj_descripcio = rs.getString("proj_descripcio");
                Integer usu_cap_projecte = rs.getInt("usu_cap_projecte");
                return new Projecte (proj_id, proj_nom, proj_descripcio, getUsuari(usu_cap_projecte));
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public List<Estat> getLlistaEstats(String sessionId) throws GestioProjectesException {
        if (sessionId == null) return null;
        
        String loginUsuari = buscarUsuari(sessionId);
        if (loginUsuari == null) return null;
        
        try {
            String consulta = "select stat_id, stat_nom from estat";
            PreparedStatement pst = con.prepareStatement(consulta);
            ResultSet rs = pst.executeQuery();
            
            List<Estat> estats = new ArrayList<Estat>();
            
            while (rs.next()){
                Integer stat_id = rs.getInt("stat_id");
                String stat_nom = rs.getString("stat_nom");
                
                estats.add(new Estat(stat_id, stat_nom));
            }
            
            for (Estat stat: estats) {
                hmEstats.put(stat.getId(), stat);
            }
            return estats;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public Tasca getTasca(int id) throws GestioProjectesException {
        try {
            String consulta = "select tasc_id, tasc_data_creacio, tasc_nom, tasc_descripcio, tasc_data_limit, proj_id, usu_creada_per, usu_assignada_a, stat_id from tasca where tasc_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, id);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer tasc_id = rs.getInt("tasc_id");
                Date tasc_data_creacio = rs.getDate("tasc_data_creacio");
                String tasc_nom = rs.getString("tasc_nom");
                String tasc_descripcio = rs.getString("tasc_descripcio");
                Date tasc_data_limit = rs.getDate("tasc_data_limit");
                Integer proj_id = rs.getInt("proj_id");
                Integer usu_creada_per = rs.getInt("usu_creada_per");
                Integer usu_assignada_a = rs.getInt("usu_assignada_a");
                Integer stat_id = rs.getInt("stat_id");
                
                return new Tasca (tasc_id, tasc_data_creacio, tasc_nom, tasc_descripcio, tasc_data_limit, 
                        getUsuari(usu_creada_per), getUsuari(usu_assignada_a), getEstat(stat_id));
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public List<Entrada> getLlistaEntrades(String sessionId, Integer idTasca) throws GestioProjectesException {

        if (sessionId == null) return null;
        
        String loginUsuari = buscarUsuari(sessionId);
        if (loginUsuari == null) return null;
        
        try {
            String consulta = "select entr_numeracio, entr_data, entr_entrada, usu_escrita_per, usu_nova_assignacio, stat_id from entrada where tasc_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, idTasca);
            ResultSet rs = pst.executeQuery();
            
            List<Entrada> entrades = new ArrayList<Entrada>();
            
            while (rs.next()){
                Integer entr_numeracio = rs.getInt("entr_numeracio");
                Date entr_data = rs.getDate("entr_data");
                String entr_entrada = rs.getString("entr_entrada");
                Integer usu_escrita_per = rs.getInt("usu_escrita_per");
                Integer usu_nova_assignacio = rs.getInt("usu_nova_assignacio");
                Integer stat_id = rs.getInt("stat_id");
                
                entrades.add(new Entrada(entr_numeracio, entr_data, entr_entrada, getUsuari(usu_escrita_per), 
                                         getUsuari(usu_nova_assignacio), getEstat(stat_id)));
            }
            return entrades;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public List<Usuari> getLlistaUsuaris(String sessionId) throws GestioProjectesException {
    
        if (sessionId == null) return null;
        
        String loginUsuari = buscarUsuari(sessionId);
        if (loginUsuari == null) return null;
        
        try {
            String consulta = "select usu_id, usu_nom, usu_cognom_1, usu_cognom_2, usu_data_naixement, usu_login, usu_password_hash from usuari";
            PreparedStatement pst = con.prepareStatement(consulta);
            ResultSet rs = pst.executeQuery();
            
            List<Usuari> usuaris = new ArrayList<Usuari>();
            
            while (rs.next()){

                Integer usu_id = rs.getInt("usu_id");
                String usu_nom = rs.getString("usu_nom");
                String usu_cognom_1 = rs.getString("usu_cognom_1");
                String usu_cognom_2 = rs.getString("usu_cognom_2");
                Date usu_data_naixement = rs.getDate("usu_data_naixement");
                String usu_login = rs.getString("usu_login");
                String usu_password_hash = rs.getString("usu_password_hash");
                
                usuaris.add(new Usuari(usu_id, usu_nom, usu_cognom_1, usu_cognom_2, usu_data_naixement, usu_login, usu_password_hash));
            }
            
            for (Usuari usu: usuaris) {
                hmUsuaris.put(usu.getId(), usu);
            }
            
            return usuaris;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }    
    }
    
    @Override
    public Entrada getEntrada(int idTasca, int idEntrada) throws GestioProjectesException {
        try {
            String consulta = "select entr_numeracio, entr_data, entr_entrada, usu_escrita_per, usu_nova_assignacio, stat_id from entrada where tasc_id = ? and entr_numeracio = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, idTasca);
            pst.setInt(2, idEntrada);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer entr_numeracio = rs.getInt("entr_numeracio");
                Date entr_data = rs.getDate("entr_data");
                String entr_entrada = rs.getString("entr_entrada");
                Integer usu_escrita_per = rs.getInt("usu_escrita_per");
                Integer usu_nova_assignacio = rs.getInt("usu_nova_assignacio");
                Integer stat_id = rs.getInt("stat_id");
                
                return new Entrada(entr_numeracio, entr_data, entr_entrada, getUsuari(usu_escrita_per), 
                                   getUsuari(usu_nova_assignacio), getEstat(stat_id));
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public Usuari getUsuari(String sessionId) throws GestioProjectesException {
        
        if (sessionId == null) return null;
        
        String loginUsuari = buscarUsuari(sessionId);
        if (loginUsuari == null) return null;
        
        try {
            
            String consulta = "select usu_id, usu_nom, usu_cognom_1, usu_cognom_2, usu_data_naixement, usu_login, usu_password_hash from usuari where usu_login = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setString(1, loginUsuari);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                Integer usu_id = rs.getInt("usu_id");
                String usu_nom = rs.getString("usu_nom");
                String usu_cognom_1 = rs.getString("usu_cognom_1");
                String usu_cognom_2 = rs.getString("usu_cognom_2");
                Date usu_data_naixement = rs.getDate("usu_data_naixement");
                String usu_login = rs.getString("usu_login");
                String usu_password_hash = rs.getString("usu_password_hash");
                
                return new Usuari(usu_id, usu_nom, usu_cognom_1, usu_cognom_2, usu_data_naixement, usu_login, usu_password_hash);
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public Integer nextNumeracioEntrada(Integer idTasca) throws GestioProjectesException {
                        
        try {
            
            String consulta = "select max(entr_numeracio)+1 as max_numeracio from entrada where tasc_id = ?";
            PreparedStatement pst = con.prepareStatement(consulta);
            pst.setInt(1, idTasca);
            ResultSet rs = pst.executeQuery();
            
            if (rs.next()){
                return rs.getInt("max_numeracio");
            }
            
            return null;
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la consulta: " + ex.getMessage());
        }
    }
    
    @Override
    public void afegirEntrada(int idTasca, Entrada entrada) throws GestioProjectesException {
        
        try {
            
            String insert = "insert into entrada (tasc_id, entr_numeracio, entr_data, entr_entrada,"
                + "usu_escrita_per, usu_nova_assignacio, stat_id)"
                + "values (?, ?, ?, ?, ?, ?, ?)";
                
            PreparedStatement pstmt = con.prepareStatement(insert);

            pstmt.setInt(1, idTasca);
            pstmt.setInt(2, entrada.getNumero());
            pstmt.setDate(3, new java.sql.Date(entrada.getData().getTime()));
            pstmt.setString(4, entrada.getEntrada());
            pstmt.setInt(5, entrada.getEscriptor().getId());
            if (entrada.getNovaAssignacio() != null) {
                pstmt.setInt(6, entrada.getNovaAssignacio().getId());
            } else {
                pstmt.setObject(6, null);
            }
            if (entrada.getNouEstat() != null) {
                pstmt.setInt(7, entrada.getNouEstat().getId());
            } else {
                pstmt.setObject(7, null);
            }

            int affectedRows = pstmt.executeUpdate();
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la insersio: " + ex.getMessage());
        }

    }

    @Override
    public void modificarEntrada(int idTasca, Entrada entrada) throws GestioProjectesException {
        try {
            
            String insert = "update entrada "
                                 + "set entr_data = ?, "
                                 + "entr_entrada = ?, "
                                 + "usu_escrita_per = ?,"
                                 + "usu_nova_assignacio = ?, "
                                 + "stat_id = ? "
                          + "where tasc_id = ? and entr_numeracio = ?";
                
            PreparedStatement pstmt = con.prepareStatement(insert);

            pstmt.setDate(1, new java.sql.Date(entrada.getData().getTime()));
            pstmt.setString(2, entrada.getEntrada());
            pstmt.setInt(3, entrada.getEscriptor().getId());
            if (entrada.getNovaAssignacio() != null) {
                pstmt.setInt(4, entrada.getNovaAssignacio().getId());
            } else {
                pstmt.setObject(4, null);
            }
            if (entrada.getNouEstat() != null) {
                pstmt.setInt(5, entrada.getNouEstat().getId());
            } else {
                pstmt.setObject(5, null);
            }
            pstmt.setInt(6, idTasca);
            pstmt.setInt(7, entrada.getNumero());
            
            int affectedRows = pstmt.executeUpdate();
            
        } catch (SQLException ex) {
            throw new GestioProjectesException("Error en la insersio: " + ex.getMessage());
        }
    }
    
    
    @Override
    public List<Usuari> getLlistaUsuaris() throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void addUsuari(Usuari nouUsuari) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void deleteUsuari(int id) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void modificarUsuari(Usuari usuari) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Projecte> getLlistaProjectes() throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Projecte> getLlistaProjectesAssignats(Usuari usuari) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public List<Projecte> getLlistaProjectesNoAssignats(Usuari usuari) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void assignarProjecte(Usuari usuari, Projecte projecte, Rol rol) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void desassignarProjecte(Usuari usuari, Projecte projecte) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public List<Rol> getLlistaRols() throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public Rol getRol(int id) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public Rol getRolAssignat(Usuari usuari, Projecte projecte) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    @Override
    public boolean existeixUsuari(int id) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public boolean existeixProjecte(int id) throws GestioProjectesException {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
