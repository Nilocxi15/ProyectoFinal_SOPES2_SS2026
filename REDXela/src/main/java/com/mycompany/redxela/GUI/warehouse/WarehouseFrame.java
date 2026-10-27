package com.mycompany.redxela.GUI.warehouse;

import com.mycompany.redxela.GUI.Home;
import com.mycompany.redxela.GUI.deadlock.DeadlockFrame;
import com.mycompany.redxela.GUI.orders.OrdersFrame;
import com.mycompany.redxela.ipc.StockArrivalEvent;
import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.Warehouse;
import com.mycompany.redxela.models.WarehouseBlock;
import com.mycompany.redxela.simulation.SimulationController;
import com.mycompany.redxela.util.ProductCatalog;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

public class WarehouseFrame extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(WarehouseFrame.class.getName());

    // Componentes del Formulario de Recepción (Pipe IPC)
    private JComboBox<Product> cmbProductReceipt;
    private JSpinner spnQuantityReceipt;

    // Componentes de Tablas y Visualización
    private JTable tblCatalogStock;
    private DefaultTableModel catalogTableModel;

    private JComboBox<String> cmbAisleFilter;
    private JLabel lblWarehouseOccupancy;
    private JTable tblWarehouseBlocks;
    private DefaultTableModel warehouseTableModel;

    private JTextArea txtPipeLog;
    private Timer refreshTimer;

    /**
     * Creates new form WarehouseFrame
     */
    public WarehouseFrame() {
        initComponents();
        setupCustomUI();
        this.setTitle("Mercadería y Almacén | REDXela");
        this.pack();
        this.setSize(1000, 680);
        this.setLocationRelativeTo(null);

        // Iniciar temporizador de actualización periódica para reflejar cambios en tiempo real
        refreshTimer = new Timer(1500, e -> refreshData());
        refreshTimer.start();
    }

    private void setupCustomUI() {
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout(8, 8));

        JPanel mainPanel = new JPanel(new BorderLayout(8, 8));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // PANEL NORTE: Formulario de Recepción de Mercadería (Tubería / Pipe IPC)        
        JPanel receptionPanel = new JPanel(new GridBagLayout());
        receptionPanel.setBorder(BorderFactory.createTitledBorder("Recepción de Mercadería (Área de Descarga -> Tubería IPC -> Inventario)"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.5;
        receptionPanel.add(new JLabel("Producto que arriba en el Camión:"), gbc);
        gbc.gridx = 1;
        gbc.weightx = 0.2;
        receptionPanel.add(new JLabel("Cantidad de Unidades:"), gbc);
        gbc.gridx = 2;
        gbc.weightx = 0.3;
        receptionPanel.add(new JLabel("Acción IPC:"), gbc);

        gbc.gridx = 0;
        gbc.gridy = 1;
        cmbProductReceipt = new JComboBox<>(ProductCatalog.getAll().toArray(new Product[0]));
        receptionPanel.add(cmbProductReceipt, gbc);

        gbc.gridx = 1;
        spnQuantityReceipt = new JSpinner(new SpinnerNumberModel(10, 1, 100, 1));
        receptionPanel.add(spnQuantityReceipt, gbc);

        gbc.gridx = 2;
        JButton btnReceiveStock = new JButton("Descargar Camión (Transmitir por Pipe)");
        btnReceiveStock.setFont(btnReceiveStock.getFont().deriveFont(java.awt.Font.BOLD));
        btnReceiveStock.addActionListener(e -> handleReceiveStock());
        receptionPanel.add(btnReceiveStock, gbc);

        mainPanel.add(receptionPanel, BorderLayout.NORTH);

        // PANEL CENTRO: SplitPane (Izquierda: Catálogo y Bitácora | Derecha: Almacén)        
        // Panel Izquierdo: Inventario Consolidado + Bitácora Pipe
        JPanel leftPanel = new JPanel(new BorderLayout(6, 6));
        leftPanel.setPreferredSize(new Dimension(460, 480));

        // Tabla de existencias de catálogo
        String[] catalogCols = {"Producto", "Mercancía", "Stock Disp.", "Unidades/Bloque"};
        catalogTableModel = new DefaultTableModel(catalogCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblCatalogStock = new JTable(catalogTableModel);
        tblCatalogStock.setRowHeight(22);
        JScrollPane scrollCatalog = new JScrollPane(tblCatalogStock);
        scrollCatalog.setBorder(BorderFactory.createTitledBorder("Existencias del Catálogo"));
        leftPanel.add(scrollCatalog, BorderLayout.CENTER);

        // Bitácora de la Tubería IPC
        JPanel logPanel = new JPanel(new BorderLayout(4, 4));
        logPanel.setBorder(BorderFactory.createTitledBorder("Bitácora de Eventos Tubería IPC (PipedWriter -> PipedReader)"));
        logPanel.setPreferredSize(new Dimension(460, 180));
        txtPipeLog = new JTextArea();
        txtPipeLog.setEditable(false);
        txtPipeLog.setFont(new java.awt.Font("Monospaced", java.awt.Font.PLAIN, 12));
        JScrollPane scrollLog = new JScrollPane(txtPipeLog);
        logPanel.add(scrollLog, BorderLayout.CENTER);
        leftPanel.add(logPanel, BorderLayout.SOUTH);

        // Panel Derecho: Almacén No Contiguo (72 Bloques Físicos)
        JPanel rightPanel = new JPanel(new BorderLayout(6, 6));
        rightPanel.setBorder(BorderFactory.createTitledBorder("Mapa Físico del Almacén No Contiguo (3 Pasillos x 3 Niveles x 8 Bahías = 72 Bloques)"));

        // Barra de control de filtro por pasillo y ocupación
        JPanel filterPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        filterPanel.add(new JLabel("Filtrar por Pasillo:"));
        cmbAisleFilter = new JComboBox<>(new String[]{"Todos los Pasillos", "Pasillo A", "Pasillo B", "Pasillo C"});
        cmbAisleFilter.addActionListener(e -> refreshWarehouseTable());
        filterPanel.add(cmbAisleFilter);

        lblWarehouseOccupancy = new JLabel("Ocupación: Calculando...");
        lblWarehouseOccupancy.setFont(lblWarehouseOccupancy.getFont().deriveFont(java.awt.Font.BOLD));
        filterPanel.add(lblWarehouseOccupancy);

        rightPanel.add(filterPanel, BorderLayout.NORTH);

        // Tabla detallada de bloques físicos del almacén
        String[] warehouseCols = {"Ubicación", "Pasillo", "Nivel", "Bahía", "Estado", "Producto Almacenado", "Unidades"};
        warehouseTableModel = new DefaultTableModel(warehouseCols, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblWarehouseBlocks = new JTable(warehouseTableModel);
        tblWarehouseBlocks.setRowHeight(22);

        // Renderizador para colorear estado Libre (verde) y Ocupado (naranja/rojo)
        tblWarehouseBlocks.getColumnModel().getColumn(4).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public java.awt.Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                java.awt.Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if ("LIBRE".equals(value)) {
                    c.setForeground(new Color(40, 167, 69));
                } else {
                    c.setForeground(new Color(220, 53, 69));
                }
                return c;
            }
        });

        JScrollPane scrollWarehouse = new JScrollPane(tblWarehouseBlocks);
        rightPanel.add(scrollWarehouse, BorderLayout.CENTER);

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setResizeWeight(0.45);
        mainPanel.add(splitPane, BorderLayout.CENTER);

        getContentPane().add(mainPanel, BorderLayout.CENTER);

        // Suscribirse al evento de llegada por Pipe IPC para registrar en la bitácora
        SimulationController.getInstance().addStockEventListener(this::logStockArrival);

        // Carga inicial de datos
        refreshData();
    }

    private void handleReceiveStock() {
        Product selectedProduct = (Product) cmbProductReceipt.getSelectedItem();
        if (selectedProduct == null) {
            JOptionPane.showMessageDialog(this, "Seleccione un producto del catálogo.",
                    "Aviso", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quantity = (Integer) spnQuantityReceipt.getValue();

        // Enviar cargamento a través de la Tubería IPC
        boolean sent = SimulationController.getInstance().receiveMerchandise(selectedProduct, quantity);

        if (sent) {
            JOptionPane.showMessageDialog(this,
                    "Cargamento transmitido con éxito por la Tubería (Pipe IPC):\n"
                    + "Producto: " + selectedProduct.getName() + "\n"
                    + "Cantidad: +" + quantity + " unidades.\n"
                    + "El Área de Inventario leerá el flujo, actualizará el stock y asignará los bloques físicos en el almacén.",
                    "Transmisión IPC Exitosa", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Error al transmitir por la tubería IPC.",
                    "Fallo IPC", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void logStockArrival(StockArrivalEvent event) {
        SwingUtilities.invokeLater(() -> {
            String time = event.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            StringBuilder sb = new StringBuilder();
            sb.append("[").append(time).append("] [PIPE IPC -> INVENTARIO] Recibidas +")
                    .append(event.getQuantity()).append(" uds de '")
                    .append(event.getProduct().getName()).append("'.\n")
                    .append("   -> Asignado en ").append(event.getAllocatedBlocks().size()).append(" bloque(s) no contiguo(s): ");

            for (int i = 0; i < event.getAllocatedBlocks().size(); i++) {
                sb.append(event.getAllocatedBlocks().get(i).getLocationCode());
                if (i < event.getAllocatedBlocks().size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("\n");

            txtPipeLog.append(sb.toString());
            txtPipeLog.setCaretPosition(txtPipeLog.getDocument().getLength());

            refreshData();
        });
    }

    private void refreshData() {
        refreshCatalogTable();
        refreshWarehouseTable();
    }

    private void refreshCatalogTable() {
        catalogTableModel.setRowCount(0);
        for (Product product : ProductCatalog.getAll()) {
            catalogTableModel.addRow(new Object[]{
                product.getName(),
                product.getMerchandise().getName(),
                product.getCurrentStock(),
                product.getUnitsPerBlock()
            });
        }
    }

    private void refreshWarehouseTable() {
        warehouseTableModel.setRowCount(0);
        Warehouse warehouse = SimulationController.getInstance().getWarehouse();
        List<WarehouseBlock> allBlocks = warehouse.getAllBlocks();

        String selectedFilter = (String) cmbAisleFilter.getSelectedItem();
        String targetAisle = null;
        if ("Pasillo A".equals(selectedFilter)) {
            targetAisle = "A";
        } else if ("Pasillo B".equals(selectedFilter)) {
            targetAisle = "B";
        } else if ("Pasillo C".equals(selectedFilter)) {
            targetAisle = "C";
        }

        for (WarehouseBlock block : allBlocks) {
            if (targetAisle != null && !block.getAisle().equals(targetAisle)) {
                continue;
            }

            String estado = block.isFree() ? "LIBRE" : "OCUPADO";
            String producto = block.isFree() ? "-" : block.getStoredProduct().getName();
            String unidades = block.isFree() ? "0" : (block.getCurrentUnits() + " / " + block.getStoredProduct().getUnitsPerBlock());

            warehouseTableModel.addRow(new Object[]{
                block.getLocationCode(),
                "Pasillo " + block.getAisle(),
                "Nivel " + block.getLevel(),
                "Bahía " + block.getSlot(),
                estado,
                producto,
                unidades
            });
        }

        int occupied = warehouse.getOccupiedBlocksCount();
        int total = Warehouse.TOTAL_CAPACITY_BLOCKS;
        double pct = warehouse.getOccupancyPercentage();
        lblWarehouseOccupancy.setText(String.format("Ocupación: %d/%d bloques (%.1f%%)", occupied, total, pct));
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
        deadlocksMenu = new javax.swing.JMenu();

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

        java.awt.EventQueue.invokeLater(() -> new WarehouseFrame().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu deadlocksMenu;
    private javax.swing.JMenu homeMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenu ordersMenu;
    // End of variables declaration//GEN-END:variables
}
