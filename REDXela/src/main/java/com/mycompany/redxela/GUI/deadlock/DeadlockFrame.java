package com.mycompany.redxela.GUI.deadlock;

import com.mycompany.redxela.GUI.Home;
import com.mycompany.redxela.GUI.orders.OrdersFrame;
import com.mycompany.redxela.GUI.warehouse.WarehouseFrame;
import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.resources.DeadlockEvent;
import com.mycompany.redxela.simulation.SimulationController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

public class DeadlockFrame extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(DeadlockFrame.class.getName());

    private JLabel lblStatusBanner;
    private JTextArea txtDeadlockDetails;
    private JButton btnTriggerDeadlock;
    private JButton btnResolveWinnerA;
    private JButton btnResolveWinnerB;
    private JLabel lblResolutionPrompt;

    private DeadlockEvent currentDeadlock;
    private Timer refreshTimer;

    /**
     * Creates new form DeadlockFrame
     */
    public DeadlockFrame() {
        initComponents();
        setupCustomUI();
        this.setTitle("Conflicto de Recursos | REDXela");
        this.pack();
        this.setSize(920, 620);
        this.setLocationRelativeTo(null);

        // Suscribirse al listener de Deadlock para reaccionar inmediatamente en la GUI
        SimulationController.getInstance().addDeadlockListener(event -> {
            SwingUtilities.invokeLater(() -> updateDeadlockUI(event));
        });

        // Temporizador de refresco periódico
        refreshTimer = new Timer(1000, e -> checkCurrentDeadlockState());
        refreshTimer.start();

        checkCurrentDeadlockState();
    }

    private void setupCustomUI() {
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout(10, 10));

        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        
        // PANEL NORTE: Título, Botón de Provocación y Banner de Estado        
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));

        JPanel headerPanel = new JPanel(new BorderLayout(10, 5));
        JLabel lblTitle = new JLabel("Detección y Resolución de Interbloqueos (Deadlocks)");
        lblTitle.setFont(lblTitle.getFont().deriveFont(Font.BOLD, 17f));
        headerPanel.add(lblTitle, BorderLayout.WEST);

        btnTriggerDeadlock = new JButton("Provocar Conflicto de Recursos Deliberado");
        btnTriggerDeadlock.setFont(btnTriggerDeadlock.getFont().deriveFont(Font.BOLD, 13f));
        btnTriggerDeadlock.setBackground(new Color(220, 53, 69));
        btnTriggerDeadlock.setForeground(Color.WHITE);
        btnTriggerDeadlock.setToolTipText("Inyecta simultáneamente un pedido Electrónico y uno de Carga Especial en orden inverso de adquisición.");
        btnTriggerDeadlock.addActionListener(e -> handleTriggerDeadlock());
        headerPanel.add(btnTriggerDeadlock, BorderLayout.EAST);
        topPanel.add(headerPanel);

        topPanel.add(Box.createVerticalStrut(10));

        lblStatusBanner = new JLabel("ESTADO DEL SISTEMA: OPERACIÓN NORMAL (Sin interbloqueos)", SwingConstants.CENTER);
        lblStatusBanner.setOpaque(true);
        lblStatusBanner.setBackground(new Color(40, 167, 69));
        lblStatusBanner.setForeground(Color.WHITE);
        lblStatusBanner.setFont(lblStatusBanner.getFont().deriveFont(Font.BOLD, 14f));
        lblStatusBanner.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        topPanel.add(lblStatusBanner);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        
        // PANEL CENTRO: Diagnóstico Teórico y Grafo de Asignación (RAG)        
        JPanel centerPanel = new JPanel(new BorderLayout(6, 6));
        centerPanel.setBorder(BorderFactory.createTitledBorder("Diagnóstico del Grafo de Asignación de Recursos (RAG)"));

        txtDeadlockDetails = new JTextArea();
        txtDeadlockDetails.setEditable(false);
        txtDeadlockDetails.setFont(new Font("Monospaced", Font.PLAIN, 13));
        txtDeadlockDetails.setLineWrap(true);
        txtDeadlockDetails.setWrapStyleWord(true);
        txtDeadlockDetails.setText(getNormalStateExplanation());
        JScrollPane scrollDetails = new JScrollPane(txtDeadlockDetails);
        centerPanel.add(scrollDetails, BorderLayout.CENTER);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        
        // PANEL SUR: Resolución Manual (Preemption & Rollback)        
        JPanel resolutionPanel = new JPanel(new BorderLayout(6, 6));
        resolutionPanel.setBorder(BorderFactory.createTitledBorder("Resolución Manual del Conflicto (Desapropiación con Retroceso / Preemption & Rollback)"));

        lblResolutionPrompt = new JLabel("Para resolver el conflicto, seleccione manualmente cuál de las dos operaciones en disputa continuará:", SwingConstants.CENTER);
        lblResolutionPrompt.setFont(lblResolutionPrompt.getFont().deriveFont(Font.BOLD, 12f));
        resolutionPanel.add(lblResolutionPrompt, BorderLayout.NORTH);

        JPanel buttonsPanel = new JPanel(new GridLayout(1, 2, 14, 0));
        buttonsPanel.setPreferredSize(new Dimension(860, 50));

        btnResolveWinnerA = new JButton("Continuar con Pedido A");
        btnResolveWinnerA.setFont(btnResolveWinnerA.getFont().deriveFont(Font.BOLD, 13f));
        btnResolveWinnerA.setEnabled(false);
        btnResolveWinnerA.addActionListener(e -> handleResolve(true));

        btnResolveWinnerB = new JButton("Continuar con Pedido B");
        btnResolveWinnerB.setFont(btnResolveWinnerB.getFont().deriveFont(Font.BOLD, 13f));
        btnResolveWinnerB.setEnabled(false);
        btnResolveWinnerB.addActionListener(e -> handleResolve(false));

        buttonsPanel.add(btnResolveWinnerA);
        buttonsPanel.add(btnResolveWinnerB);
        resolutionPanel.add(buttonsPanel, BorderLayout.CENTER);

        mainPanel.add(resolutionPanel, BorderLayout.SOUTH);

        getContentPane().add(mainPanel, BorderLayout.CENTER);
    }

    private String getNormalStateExplanation() {
        return "El sistema se encuentra operando bajo condiciones normales.\n\n"
                + "En este momento no existen ciclos de espera circular en el Grafo de Asignación de Recursos (RAG).\n"
                + "Los semáforos se están asignando y liberando conforme los pedidos avanzan en su ciclo de vida.\n\n"
                + "Para provocar deliberadamente una situación de interbloqueo y poner a prueba los algoritmos de detección,\n"
                + "presione el botón rojo superior 'Provocar Conflicto de Recursos Deliberado'.";
    }

    private void handleTriggerDeadlock() {
        SimulationController.getInstance().triggerDeliberateDeadlock();
        JOptionPane.showMessageDialog(this,
                "Se han inyectado simultáneamente dos pedidos en competencia cruzada:\n"
                + "• Pedido Electrónico: solicita primero Escáner de Códigos y luego Montacargas.\n"
                + "• Pedido Carga Especial: solicita primero Montacargas y luego Escáner de Códigos.\n\n"
                + "Ambos procesos entrarán en la condición de Espera Circular (Coffman) en breve.",
                "Inyección de Conflicto", JOptionPane.WARNING_MESSAGE);
    }

    private void checkCurrentDeadlockState() {
        DeadlockEvent deadlock = SimulationController.getInstance().getResourceManager().getActiveDeadlock();
        updateDeadlockUI(deadlock);
    }

    private void updateDeadlockUI(DeadlockEvent deadlock) {
        this.currentDeadlock = deadlock;

        if (deadlock != null) {
            lblStatusBanner.setText("¡INTERBLOQUEO (DEADLOCK) ACTIVO DETECTADO! - Espera Circular de Coffman");
            lblStatusBanner.setBackground(new Color(220, 53, 69));

            Order orderA = deadlock.getOrderA();
            Order orderB = deadlock.getOrderB();

            txtDeadlockDetails.setText(deadlock.getExplanation());

            btnResolveWinnerA.setText("<html><center>Continuar con <b>Pedido #" + orderA.getId().toString().substring(0, 8) + " (" + orderA.getMerchandiseType().getName() + ")</b><br><small>(Expulsar Pedido #" + orderB.getId().toString().substring(0, 8) + " a la cola de espera)</small></center></html>");
            btnResolveWinnerB.setText("<html><center>Continuar con <b>Pedido #" + orderB.getId().toString().substring(0, 8) + " (" + orderB.getMerchandiseType().getName() + ")</b><br><small>(Expulsar Pedido #" + orderA.getId().toString().substring(0, 8) + " a la cola de espera)</small></center></html>");

            btnResolveWinnerA.setEnabled(true);
            btnResolveWinnerB.setEnabled(true);
            btnTriggerDeadlock.setEnabled(false);

        } else {
            lblStatusBanner.setText("ESTADO DEL SISTEMA: OPERACIÓN NORMAL (Sin interbloqueos)");
            lblStatusBanner.setBackground(new Color(40, 167, 69));

            txtDeadlockDetails.setText(getNormalStateExplanation());
            btnResolveWinnerA.setText("Continuar con Pedido A");
            btnResolveWinnerB.setText("Continuar con Pedido B");
            btnResolveWinnerA.setEnabled(false);
            btnResolveWinnerB.setEnabled(false);
            btnTriggerDeadlock.setEnabled(true);
        }
    }

    private void handleResolve(boolean winnerIsA) {
        if (currentDeadlock == null) {
            return;
        }

        Order winner = winnerIsA ? currentDeadlock.getOrderA() : currentDeadlock.getOrderB();
        Order loser = winnerIsA ? currentDeadlock.getOrderB() : currentDeadlock.getOrderA();

        // Ejecutar Preemption & Rollback mediante el controlador
        SimulationController.getInstance().resolveDeadlock(winner, loser);

        JOptionPane.showMessageDialog(this,
                "Interbloqueo resuelto manualmente aplicando Desapropiación con Retroceso (Preemption & Rollback):\n\n"
                + "• GANADOR: Pedido #" + winner.getId().toString().substring(0, 8) + " continuará adquiriendo los recursos y será procesado.\n"
                + "• EXPULSADO: Pedido #" + loser.getId().toString().substring(0, 8) + " liberó sus recursos de vuelta al semáforo y regresó a la lista de espera para ser atendido más adelante.",
                "Deadlock Resuelto", JOptionPane.INFORMATION_MESSAGE);

        updateDeadlockUI(null);
    }

    @Override
    public void dispose() {
        if (refreshTimer != null && refreshTimer.isRunning()) {
            refreshTimer.stop();
        }
        super.dispose();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jMenuBar1 = new javax.swing.JMenuBar();
        homeMenu = new javax.swing.JMenu();
        ordersMenu = new javax.swing.JMenu();
        warehouseMenu = new javax.swing.JMenu();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

        homeMenu.setText("Inicio");
        homeMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                homeMenuMouseClicked(evt);
            }
        });
        jMenuBar1.add(homeMenu);

        ordersMenu.setText("Ingresar Pedido");
        ordersMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                ordersMenuMouseClicked(evt);
            }
        });
        jMenuBar1.add(ordersMenu);

        warehouseMenu.setText("Mercancía y Almacén");
        warehouseMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                warehouseMenuMouseClicked(evt);
            }
        });
        jMenuBar1.add(warehouseMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 275, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void homeMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_homeMenuMouseClicked
        dispose();
        Home home = new Home();
        home.setVisible(true);
    }//GEN-LAST:event_homeMenuMouseClicked

    private void ordersMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_ordersMenuMouseClicked
        dispose();
        OrdersFrame ordersFrame = new OrdersFrame();
        ordersFrame.setVisible(true);
    }//GEN-LAST:event_ordersMenuMouseClicked

    private void warehouseMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_warehouseMenuMouseClicked
        dispose();
        WarehouseFrame warehouseFrame = new WarehouseFrame();
        warehouseFrame.setVisible(true);
    }//GEN-LAST:event_warehouseMenuMouseClicked

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException ex) {
            logger.log(java.util.logging.Level.SEVERE, null, ex);
        }

        java.awt.EventQueue.invokeLater(() -> new DeadlockFrame().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu homeMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenu ordersMenu;
    private javax.swing.JMenu warehouseMenu;
    // End of variables declaration//GEN-END:variables
}
