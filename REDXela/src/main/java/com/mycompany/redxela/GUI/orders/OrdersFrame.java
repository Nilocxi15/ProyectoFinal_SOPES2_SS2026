package com.mycompany.redxela.GUI.orders;

import com.mycompany.redxela.GUI.Home;
import com.mycompany.redxela.GUI.deadlock.DeadlockFrame;
import com.mycompany.redxela.GUI.warehouse.WarehouseFrame;
import com.mycompany.redxela.models.Client;
import com.mycompany.redxela.models.Merchandise;
import com.mycompany.redxela.models.Order;
import com.mycompany.redxela.models.Product;
import com.mycompany.redxela.models.ServiceTier;
import com.mycompany.redxela.simulation.SimulationController;
import com.mycompany.redxela.util.MerchandiseCatalog;
import com.mycompany.redxela.util.ProductCatalog;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

public class OrdersFrame extends javax.swing.JFrame {

    private static final java.util.logging.Logger logger = java.util.logging.Logger.getLogger(OrdersFrame.class.getName());

    // Componentes visuales personalizados
    private JTextField txtClientName;
    private JTextField txtClientPhone;
    private JTextField txtClientAddress;
    private JComboBox<ServiceTier> cmbServiceTier;
    private JComboBox<Merchandise> cmbMerchandise;
    private JComboBox<Product> cmbProduct;
    private JSpinner spnQuantity;
    private JTable tblItems;
    private DefaultTableModel itemsTableModel;
    private JLabel lblTotalUnits;

    // Estructura de items en preparación para el pedido actual
    private final Map<Product, Integer> orderItems = new LinkedHashMap<>();

    /**
     * Creates new form OrdersFrame
     */
    public OrdersFrame() {
        initComponents();
        setupCustomUI();
        this.setTitle("Ingreso de Pedido | REDXela");
        this.pack();
        this.setSize(920, 600);
        this.setLocationRelativeTo(null);
    }

    private void setupCustomUI() {
        getContentPane().removeAll();
        getContentPane().setLayout(new BorderLayout(10, 10));

        // Panel Principal con margen
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        // PANEL IZQUIERDO: Formulario de Cliente, Servicio y Mercancía        
        JPanel leftPanel = new JPanel(new GridBagLayout());
        leftPanel.setBorder(BorderFactory.createTitledBorder("1. Datos del Cliente y Servicio"));
        leftPanel.setPreferredSize(new Dimension(380, 500));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 6, 6, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        // Nombre del cliente
        gbc.gridx = 0;
        gbc.gridy = 0;
        leftPanel.add(new JLabel("Nombre del Cliente:"), gbc);
        gbc.gridy = 1;
        txtClientName = new JTextField();
        leftPanel.add(txtClientName, gbc);

        // Teléfono
        gbc.gridy = 2;
        leftPanel.add(new JLabel("Teléfono de Contacto:"), gbc);
        gbc.gridy = 3;
        txtClientPhone = new JTextField();
        leftPanel.add(txtClientPhone, gbc);

        // Dirección
        gbc.gridy = 4;
        leftPanel.add(new JLabel("Dirección de Entrega:"), gbc);
        gbc.gridy = 5;
        txtClientAddress = new JTextField();
        leftPanel.add(txtClientAddress, gbc);

        // Nivel de Servicio (Priority Scheduling)
        gbc.gridy = 6;
        leftPanel.add(new JLabel("Nivel de Servicio (Prioridad SLA):"), gbc);
        gbc.gridy = 7;
        cmbServiceTier = new JComboBox<>(ServiceTier.values());
        leftPanel.add(cmbServiceTier, gbc);

        // Tipo de Mercancía
        gbc.gridy = 8;
        leftPanel.add(new JLabel("Tipo de Mercancía:"), gbc);
        gbc.gridy = 9;
        cmbMerchandise = new JComboBox<>(MerchandiseCatalog.getAll().toArray(new Merchandise[0]));
        cmbMerchandise.addActionListener(e -> updateProductCatalogFilter());
        leftPanel.add(cmbMerchandise, gbc);

        // Separador vertical elástico
        gbc.gridy = 10;
        gbc.weighty = 1.0;
        leftPanel.add(Box.createVerticalGlue(), gbc);

        mainPanel.add(leftPanel, BorderLayout.WEST);
        
        // PANEL DERECHO: Selección de Artículos y Tabla de Pedido        
        JPanel rightPanel = new JPanel(new BorderLayout(8, 8));
        rightPanel.setBorder(BorderFactory.createTitledBorder("2. Artículos y Detalle del Pedido"));

        // Subpanel superior para agregar producto
        JPanel addProductPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbcAdd = new GridBagConstraints();
        gbcAdd.insets = new Insets(4, 4, 4, 4);
        gbcAdd.fill = GridBagConstraints.HORIZONTAL;

        gbcAdd.gridx = 0;
        gbcAdd.gridy = 0;
        gbcAdd.weightx = 0.6;
        addProductPanel.add(new JLabel("Producto:"), gbcAdd);
        gbcAdd.gridx = 1;
        gbcAdd.weightx = 0.2;
        addProductPanel.add(new JLabel("Cantidad:"), gbcAdd);
        gbcAdd.gridx = 2;
        gbcAdd.weightx = 0.2;
        addProductPanel.add(new JLabel("Acción:"), gbcAdd);

        gbcAdd.gridx = 0;
        gbcAdd.gridy = 1;
        cmbProduct = new JComboBox<>();
        addProductPanel.add(cmbProduct, gbcAdd);

        gbcAdd.gridx = 1;
        spnQuantity = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
        addProductPanel.add(spnQuantity, gbcAdd);

        gbcAdd.gridx = 2;
        JButton btnAddProduct = new JButton("Agregar Artículo");
        btnAddProduct.addActionListener(e -> handleAddProduct());
        addProductPanel.add(btnAddProduct, gbcAdd);

        rightPanel.add(addProductPanel, BorderLayout.NORTH);

        // Tabla de detalle del pedido
        String[] columnNames = {"Producto", "Mercancía", "Cantidad", "Stock Disp."};
        itemsTableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tblItems = new JTable(itemsTableModel);
        tblItems.setRowHeight(22);
        JScrollPane scrollItems = new JScrollPane(tblItems);
        rightPanel.add(scrollItems, BorderLayout.CENTER);

        // Subpanel inferior de totales y botones de acción
        JPanel rightBottomPanel = new JPanel();
        rightBottomPanel.setLayout(new BoxLayout(rightBottomPanel, BoxLayout.Y_AXIS));

        JPanel totalPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        lblTotalUnits = new JLabel("Total Artículos (Burst Time): 0 unidades");
        lblTotalUnits.setFont(lblTotalUnits.getFont().deriveFont(java.awt.Font.BOLD, 13f));
        JButton btnRemoveItem = new JButton("Eliminar Seleccionado");
        btnRemoveItem.addActionListener(e -> handleRemoveSelectedItem());
        totalPanel.add(btnRemoveItem);
        totalPanel.add(lblTotalUnits);
        rightBottomPanel.add(totalPanel);

        JPanel actionButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        JButton btnClear = new JButton("Limpiar Formulario");
        btnClear.addActionListener(e -> resetForm());

        JButton btnSubmit = new JButton("Registrar y Despachar Pedido");
        btnSubmit.setFont(btnSubmit.getFont().deriveFont(java.awt.Font.BOLD));
        btnSubmit.addActionListener(e -> handleSubmitOrder());

        actionButtonsPanel.add(btnClear);
        actionButtonsPanel.add(btnSubmit);
        rightBottomPanel.add(actionButtonsPanel);

        rightPanel.add(rightBottomPanel, BorderLayout.SOUTH);
        mainPanel.add(rightPanel, BorderLayout.CENTER);

        getContentPane().add(mainPanel, BorderLayout.CENTER);

        // Inicializar filtro de productos
        updateProductCatalogFilter();
    }

    private void updateProductCatalogFilter() {
        cmbProduct.removeAllItems();
        Merchandise selectedMerchandise = (Merchandise) cmbMerchandise.getSelectedItem();
        if (selectedMerchandise == null) {
            return;
        }

        for (Product product : ProductCatalog.getAll()) {
            if (product.getMerchandise().equals(selectedMerchandise)) {
                cmbProduct.addItem(product);
            }
        }
    }

    private void handleAddProduct() {
        Product selectedProduct = (Product) cmbProduct.getSelectedItem();
        if (selectedProduct == null) {
            JOptionPane.showMessageDialog(this, "Debe seleccionar un producto del catálogo.",
                    "Producto requerido", JOptionPane.WARNING_MESSAGE);
            return;
        }

        int quantity = (Integer) spnQuantity.getValue();
        orderItems.put(selectedProduct, orderItems.getOrDefault(selectedProduct, 0) + quantity);

        refreshItemsTable();
    }

    private void handleRemoveSelectedItem() {
        int selectedRow = tblItems.getSelectedRow();
        if (selectedRow < 0) {
            JOptionPane.showMessageDialog(this, "Seleccione un artículo de la tabla para eliminar.",
                    "Aviso", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String productName = (String) itemsTableModel.getValueAt(selectedRow, 0);
        Product toRemove = null;
        for (Product p : orderItems.keySet()) {
            if (p.getName().equals(productName)) {
                toRemove = p;
                break;
            }
        }

        if (toRemove != null) {
            orderItems.remove(toRemove);
            refreshItemsTable();
        }
    }

    private void refreshItemsTable() {
        itemsTableModel.setRowCount(0);
        int totalUnits = 0;

        for (Map.Entry<Product, Integer> entry : orderItems.entrySet()) {
            Product p = entry.getKey();
            int qty = entry.getValue();
            totalUnits += qty;

            itemsTableModel.addRow(new Object[]{
                p.getName(),
                p.getMerchandise().getName(),
                qty,
                p.getCurrentStock()
            });
        }

        lblTotalUnits.setText("Total Artículos (Burst Time): " + totalUnits + " unidades");
    }

    private void handleSubmitOrder() {
        String name = txtClientName.getText().trim();
        String phone = txtClientPhone.getText().trim();
        String address = txtClientAddress.getText().trim();

        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Por favor ingrese el nombre del cliente.",
                    "Campo requerido", JOptionPane.WARNING_MESSAGE);
            txtClientName.requestFocus();
            return;
        }

        if (orderItems.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Debe agregar al menos un producto al pedido.",
                    "Pedido vacío", JOptionPane.WARNING_MESSAGE);
            return;
        }

        ServiceTier tier = (ServiceTier) cmbServiceTier.getSelectedItem();
        Merchandise merchandise = (Merchandise) cmbMerchandise.getSelectedItem();

        // Crear Cliente y Pedido
        Client client = new Client(UUID.randomUUID(), name, phone.isEmpty() ? "N/A" : phone, address.isEmpty() ? "N/A" : address);
        Order order = new Order(UUID.randomUUID(), client, tier, merchandise);

        for (Map.Entry<Product, Integer> entry : orderItems.entrySet()) {
            order.addProduct(entry.getKey(), entry.getValue());
        }

        // Despachar al planificador a través del controlador central
        SimulationController.getInstance().submitManualOrder(order);

        JOptionPane.showMessageDialog(this,
                "Pedido #" + order.getId().toString().substring(0, 8) + " registrado exitosamente.\n"
                + "Cliente: " + client.getName() + "\n"
                + "Nivel de Servicio: " + tier.getDisplayName() + "\n"
                + "Total Artículos: " + order.getTotalUnits() + "\n"
                + "El pedido ha ingresado a la Cola de Listos (Ready Queue).",
                "Pedido Ingresado", JOptionPane.INFORMATION_MESSAGE);

        resetForm();
    }

    private void resetForm() {
        txtClientName.setText("");
        txtClientPhone.setText("");
        txtClientAddress.setText("");
        cmbServiceTier.setSelectedIndex(0);
        cmbMerchandise.setSelectedIndex(0);
        spnQuantity.setValue(1);
        orderItems.clear();
        refreshItemsTable();
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
        warehouseMenu = new javax.swing.JMenu();
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
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 275, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void homeMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_homeMenuMouseClicked
        Home home = new Home();
        home.setVisible(true);
        this.dispose();
    }//GEN-LAST:event_homeMenuMouseClicked

    private void warehouseMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_warehouseMenuMouseClicked
        WarehouseFrame warehouseFrame = new WarehouseFrame();
        warehouseFrame.setVisible(true);
        this.dispose();
    }//GEN-LAST:event_warehouseMenuMouseClicked

    private void deadlocksMenuMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_deadlocksMenuMouseClicked
        DeadlockFrame deadlockFrame = new DeadlockFrame();
        deadlockFrame.setVisible(true);
        this.dispose();
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

        java.awt.EventQueue.invokeLater(() -> new OrdersFrame().setVisible(true));
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenu deadlocksMenu;
    private javax.swing.JMenu homeMenu;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JMenu warehouseMenu;
    // End of variables declaration//GEN-END:variables
}
