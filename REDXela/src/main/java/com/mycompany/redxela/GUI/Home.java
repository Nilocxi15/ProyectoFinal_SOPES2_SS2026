package com.mycompany.redxela.GUI;

import com.mycompany.redxela.GUI.deadlock.DeadlockFrame;
import com.mycompany.redxela.GUI.orders.OrdersFrame;
import com.mycompany.redxela.GUI.warehouse.WarehouseFrame;
import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.resources.ResourceState;
import com.mycompany.redxela.scheduler.SchedulerMetrics;
import com.mycompany.redxela.scheduler.SchedulingPolicy;
import com.mycompany.redxela.simulation.SimulationController;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.Timer;
import javax.swing.table.DefaultTableModel;

public class Home extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(Home.class.getName());

    // Componentes de la Barra Superior de Controles
    private JComboBox<SchedulingPolicy> cmbPolicy;
    private JButton btnToggleGenerator;
    private JLabel lblGeneratorStatus;

    // Tablas y Modelos del Dashboard
    private JTable tblResources;
    private DefaultTableModel resourcesTableModel;

    private JTable tblActiveOrders;
    private DefaultTableModel activeOrdersTableModel;

    private JTable tblReadyQueue;
    private DefaultTableModel readyQueueTableModel;

    // Etiquetas de Métricas y KPIs
    private JLabel lblCompletedOrders;
    private JLabel lblAvgWaitTime;
    private JLabel lblAvgProcTime;
    private JLabel lblAvgTurnaroundTime;
    private JLabel lblResourceOccupancy;
    private JLabel lblWarehouseOccupancy;

    private Timer refreshTimer;

    public Home() {
        initComponents();
        setupCustomUI();
        this.setTitle("Centro de Distribución REDXela | Dashboard de Monitoreo");
        this.pack();
        this.setSize(1100, 720);
        this.setLocationRelativeTo(null);

        // Temporizador de refresco periódico para actualización en tiempo real
        refreshTimer = new Timer(600, e -> refreshDashboard());
        refreshTimer.start();

        refreshDashboard();
    }

    private void setupCustomUI() {
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout(8, 8));

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ---------------------------------------------------------------------
        // PANEL NORTE: Barra de Control Operativo
        // ---------------------------------------------------------------------
        JPanel topControlPanel = new JPanel(new BorderLayout(10, 5));
        topControlPanel.setBorder(BorderFactory.createTitledBorder("Control Operativo y Políticas del Planificador de Procesos"));

        // Selector de política de planificación
        JPanel policyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        policyPanel.add(new JLabel("Política de Atención Activa:"));
        cmbPolicy = new JComboBox<>(SchedulingPolicy.values());
        cmbPolicy.setSelectedItem(SimulationController.getInstance().getScheduler().getCurrentPolicy());
        cmbPolicy.addActionListener(e -> {
            SchedulingPolicy selected = (SchedulingPolicy) cmbPolicy.getSelectedItem();
            if (selected != null) {
                SimulationController.getInstance().changeSchedulingPolicy(selected);
            }
        });
        policyPanel.add(cmbPolicy);
        topControlPanel.add(policyPanel, BorderLayout.WEST);

        // Controles del Generador Automático e Inyección
        JPanel generatorPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));

        lblGeneratorStatus = new JLabel("Generador: PAUSADO");
        lblGeneratorStatus.setFont(lblGeneratorStatus.getFont().deriveFont(Font.BOLD));
        generatorPanel.add(lblGeneratorStatus);

        btnToggleGenerator = new JButton("Iniciar Generador Automático");
        btnToggleGenerator.setFont(btnToggleGenerator.getFont().deriveFont(Font.BOLD));
        btnToggleGenerator.addActionListener(e -> handleToggleGenerator());
        generatorPanel.add(btnToggleGenerator);

        JButton btnInjectRandom = new JButton("Inyectar Pedido Aleatorio");
        btnInjectRandom.addActionListener(e -> {
            Order o = SimulationController.getInstance().injectSingleRandomOrder();
            JOptionPane.showMessageDialog(this,
                    "Se generó e inyectó el pedido #" + o.getId().toString().substring(0, 8)
                    + " (" + o.getServiceTier().getDisplayName() + ", " + o.getTotalUnits() + " arts.)",
                    "Pedido Inyectado", JOptionPane.INFORMATION_MESSAGE);
        });
        generatorPanel.add(btnInjectRandom);

        JButton btnQuickDeadlock = new JButton("Provocar Deadlock");
        btnQuickDeadlock.setBackground(new Color(220, 53, 69));
        btnQuickDeadlock.setForeground(Color.WHITE);
        btnQuickDeadlock.setFont(btnQuickDeadlock.getFont().deriveFont(Font.BOLD));
        btnQuickDeadlock.addActionListener(e -> {
            SimulationController.getInstance().triggerDeliberateDeadlock();
            JOptionPane.showMessageDialog(this,
                    "Conflicto de recursos inyectado deliberadamente.\n"
                    + "Vaya a la pestaña 'Conflictos de Recursos' en el menú superior para visualizar y resolver el Deadlock.",
                    "Deadlock Disparado", JOptionPane.WARNING_MESSAGE);
        });
        generatorPanel.add(btnQuickDeadlock);

        topControlPanel.add(generatorPanel, BorderLayout.EAST);
        mainPanel.add(topControlPanel, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // PANEL CENTRAL: Matriz 2x2 del Dashboard
        // ---------------------------------------------------------------------
        JPanel gridDashboard = new JPanel(new GridLayout(2, 2, 8, 8));

        // 1. Estado de Recursos Compartidos (Semáforos)
        JPanel panelResources = new JPanel(new BorderLayout());
        panelResources.setBorder(BorderFactory.createTitledBorder("Recursos Finitos Compartidos (Semáforos & Mutex)"));
        String[] resCols = {"Recurso", "Total", "Libres", "En Uso", "Ocupación %", "Ocupado por", "En Cola"};
        resourcesTableModel = new DefaultTableModel(resCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblResources = new JTable(resourcesTableModel);
        tblResources.setRowHeight(20);
        panelResources.add(new JScrollPane(tblResources), BorderLayout.CENTER);
        gridDashboard.add(panelResources);

        // 2. Pedidos en Procesamiento Actual
        JPanel panelActive = new JPanel(new BorderLayout());
        panelActive.setBorder(BorderFactory.createTitledBorder("Pedidos en Procesamiento Actual (Running)"));
        String[] activeCols = {"ID Pedido", "Cliente", "Nivel", "Mercancía", "Arts.", "Progreso (%)", "Tiempo Restante"};
        activeOrdersTableModel = new DefaultTableModel(activeCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblActiveOrders = new JTable(activeOrdersTableModel);
        tblActiveOrders.setRowHeight(20);
        panelActive.add(new JScrollPane(tblActiveOrders), BorderLayout.CENTER);
        gridDashboard.add(panelActive);

        // 3. Lista de Espera (Ready Queue)
        JPanel panelQueue = new JPanel(new BorderLayout());
        panelQueue.setBorder(BorderFactory.createTitledBorder("Lista de Espera — Ready Queue (Ordenada según Política)"));
        String[] queueCols = {"Turno", "ID Pedido", "Cliente", "Nivel", "Mercancía", "Burst Time", "Estado", "Hora Llegada"};
        readyQueueTableModel = new DefaultTableModel(queueCols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        tblReadyQueue = new JTable(readyQueueTableModel);
        tblReadyQueue.setRowHeight(20);
        panelQueue.add(new JScrollPane(tblReadyQueue), BorderLayout.CENTER);
        gridDashboard.add(panelQueue);

        // 4. Panel de Estadísticas y Métricas de Rendimiento (KPIs)
        JPanel panelKPIs = new JPanel(new BorderLayout(6, 6));
        panelKPIs.setBorder(BorderFactory.createTitledBorder("Métricas de Rendimiento y Desempeño del Planificador"));

        JPanel kpiListPanel = new JPanel();
        kpiListPanel.setLayout(new BoxLayout(kpiListPanel, BoxLayout.Y_AXIS));
        kpiListPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        lblCompletedOrders = createKpiLabel("Pedidos Completados: 0");
        lblAvgWaitTime = createKpiLabel("Tiempo Promedio de Espera (Waiting Time): 0.00 s");
        lblAvgProcTime = createKpiLabel("Tiempo Promedio de Procesamiento (Burst Time): 0.00 s");
        lblAvgTurnaroundTime = createKpiLabel("Tiempo Promedio de Retorno (Turnaround Time): 0.00 s");
        lblResourceOccupancy = createKpiLabel("Ocupación Global de Recursos: 0.0 %");
        lblWarehouseOccupancy = createKpiLabel("Ocupación del Almacén Físico: 0.0 %");

        kpiListPanel.add(lblCompletedOrders);
        kpiListPanel.add(Box.createVerticalStrut(6));
        kpiListPanel.add(lblAvgWaitTime);
        kpiListPanel.add(Box.createVerticalStrut(6));
        kpiListPanel.add(lblAvgProcTime);
        kpiListPanel.add(Box.createVerticalStrut(6));
        kpiListPanel.add(lblAvgTurnaroundTime);
        kpiListPanel.add(Box.createVerticalStrut(6));
        kpiListPanel.add(lblResourceOccupancy);
        kpiListPanel.add(Box.createVerticalStrut(6));
        kpiListPanel.add(lblWarehouseOccupancy);

        panelKPIs.add(new JScrollPane(kpiListPanel), BorderLayout.CENTER);

        JPanel kpiBottomPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btnResetMetrics = new JButton("Reiniciar Métricas");
        btnResetMetrics.addActionListener(e -> {
            SimulationController.getInstance().getMetrics().reset();
            refreshDashboard();
        });
        kpiBottomPanel.add(btnResetMetrics);
        panelKPIs.add(kpiBottomPanel, BorderLayout.SOUTH);

        gridDashboard.add(panelKPIs);

        mainPanel.add(gridDashboard, BorderLayout.CENTER);
        getContentPane().add(mainPanel, BorderLayout.CENTER);
    }

    private JLabel createKpiLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.PLAIN, 13f));
        return lbl;
    }

    private void handleToggleGenerator() {
        boolean currentlyRunning = SimulationController.getInstance().isAutoGeneratorRunning();
        boolean newState = !currentlyRunning;
        SimulationController.getInstance().setAutoGeneratorEnabled(newState);

        updateGeneratorUI(newState);
    }

    private void updateGeneratorUI(boolean isRunning) {
        if (isRunning) {
            btnToggleGenerator.setText("Pausar Generador Automático");
            btnToggleGenerator.setBackground(new Color(255, 193, 7));
            btnToggleGenerator.setForeground(Color.BLACK);
            lblGeneratorStatus.setText("Generador: ACTIVO (" + SimulationController.getInstance().getTrafficGenerator().getGeneratedCount() + " pedidos)");
            lblGeneratorStatus.setForeground(new Color(40, 167, 69));
        } else {
            btnToggleGenerator.setText("Iniciar Generador Automático");
            btnToggleGenerator.setBackground(null);
            btnToggleGenerator.setForeground(null);
            lblGeneratorStatus.setText("Generador: PAUSADO");
            lblGeneratorStatus.setForeground(Color.GRAY);
        }
    }

    private void refreshDashboard() {
        SimulationController sim = SimulationController.getInstance();

        // 1. Refrescar Recursos
        resourcesTableModel.setRowCount(0);
        List<ResourceState> resStates = sim.getResourceStates();
        for (ResourceState rs : resStates) {
            String holdersStr = rs.getActiveHoldingOrders().isEmpty() ? "Ninguno" : String.join(", ", rs.getActiveHoldingOrders());
            String waitersStr = rs.getWaitingOrders().isEmpty() ? "0 pedidos" : String.valueOf(rs.getWaitingOrders().size()) + " en espera";

            resourcesTableModel.addRow(new Object[]{
                rs.getType().getDisplayName(),
                rs.getTotalCapacity(),
                rs.getAvailablePermits(),
                rs.getInUse(),
                String.format("%.1f %%", rs.getUtilizationPercentage()),
                holdersStr,
                waitersStr
            });
        }

        // 2. Refrescar Pedidos en Procesamiento
        activeOrdersTableModel.setRowCount(0);
        List<Order> activeOrders = sim.getActiveOrders();
        for (Order o : activeOrders) {
            activeOrdersTableModel.addRow(new Object[]{
                o.getId().toString().substring(0, 8),
                o.getClient().getName(),
                o.getServiceTier().getDisplayName(),
                o.getMerchandiseType().getName(),
                o.getTotalUnits(),
                o.getProgressPercentage() + " %",
                o.getEstimatedRemainingSeconds() + " s"
            });
        }

        // 3. Refrescar Ready Queue
        readyQueueTableModel.setRowCount(0);
        List<Order> readyQueue = sim.getReadyQueue();
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        for (int i = 0; i < readyQueue.size(); i++) {
            Order o = readyQueue.get(i);
            readyQueueTableModel.addRow(new Object[]{
                "#" + (i + 1),
                o.getId().toString().substring(0, 8),
                o.getClient().getName(),
                o.getServiceTier().getDisplayName(),
                o.getMerchandiseType().getName(),
                o.getTotalUnits() + " arts.",
                o.getState(),
                o.getArrivalTime().format(dtf)
            });
        }

        // 4. Refrescar Métricas
        SchedulerMetrics m = sim.getMetrics();
        lblCompletedOrders.setText("Pedidos Completados: " + m.getTotalCompletedOrders());
        lblAvgWaitTime.setText(String.format("Tiempo Promedio de Espera (Waiting Time): %.2f s", m.getAverageWaitingTimeSeconds()));
        lblAvgProcTime.setText(String.format("Tiempo Promedio de Procesamiento (Burst Time): %.2f s", m.getAverageProcessingTimeSeconds()));
        lblAvgTurnaroundTime.setText(String.format("Tiempo Promedio de Retorno (Turnaround Time): %.2f s", m.getAverageTurnaroundTimeSeconds()));
        lblResourceOccupancy.setText(String.format("Ocupación Global de Recursos: %.1f %%", sim.getResourceManager().getOverallResourceUtilization()));
        lblWarehouseOccupancy.setText(String.format("Ocupación del Almacén Físico: %.1f %% (%d/%d bloques)",
                sim.getWarehouse().getOccupancyPercentage(),
                sim.getWarehouse().getOccupiedBlocksCount(),
                com.mycompany.redxela.models.Warehouse.TOTAL_CAPACITY_BLOCKS));

        // Actualizar estado del generador en UI
        updateGeneratorUI(sim.isAutoGeneratorRunning());
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
        ordersMenu = new javax.swing.JMenu();
        warehouseMenu = new javax.swing.JMenu();
        deadlocksMenu = new javax.swing.JMenu();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setResizable(false);

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

        deadlocksMenu.setText("Conflictos de Recursos");
        deadlocksMenu.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                deadlocksMenuMouseClicked(evt);
            }
        });
        jMenuBar1.add(deadlocksMenu);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 806, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 275, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

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

    private void deadlocksMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_deadlocksMenuMouseClicked
        dispose();
        DeadlockFrame deadlockFrame = new DeadlockFrame();
        deadlockFrame.setVisible(true);
    }//GEN-LAST:event_deadlocksMenuMouseClicked

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

        java.awt.EventQueue.invokeLater(() -> new Home().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu deadlocksMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenu ordersMenu;
    private javax.swing.JMenu warehouseMenu;
    // End of variables declaration//GEN-END:variables
}
