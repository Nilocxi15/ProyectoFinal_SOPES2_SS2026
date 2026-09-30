package com.mycompany.redxela;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.mycompany.redxela.GUI.Home;
import javax.swing.SwingUtilities;

public class REDXela {

    public static void main(String[] args) {
        // Configuración de Look and Feel de FlatLaf
        FlatDarculaLaf.setup();

        // Creación de ventana principal con Look and Feel de FlatLaf
        SwingUtilities.invokeLater(() -> {
            Home home = new Home();
            home.setVisible(true);
        });
    }
}
