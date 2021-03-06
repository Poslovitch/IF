package com.github.stefvanschie.inventoryframework;

import com.github.stefvanschie.inventoryframework.pane.OutlinePane;
import com.github.stefvanschie.inventoryframework.pane.PaginatedPane;
import com.github.stefvanschie.inventoryframework.pane.StaticPane;
import com.github.stefvanschie.inventoryframework.pane.util.Pane;
import com.github.stefvanschie.inventoryframework.util.XMLUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The base class of all GUIs
 *
 * @since 5.6.0
 */
public class Gui implements Listener, InventoryHolder {

    /**
     * A set of all panes in this inventory
     */
    private final List<Pane> panes;

    /**
     * The inventory of this gui
     */
    private Inventory inventory;

    /**
     * The consumer that will be called once a players clicks in the gui
     */
    private Consumer<InventoryClickEvent> onLocalClick;

    /**
     * The consumer that will be called once a players clicks in the gui or in their inventory
     */
    private Consumer<InventoryClickEvent> onGlobalClick;


    /**
     * The consumer that will be called once a player closes the gui
     */
    private Consumer<InventoryCloseEvent> onClose;

    /**
     * The pane mapping which will allow users to register their own panes to be used in XML files
     */
    private static final Map<String, BiFunction<Object, Element, Pane>> PANE_MAPPINGS = new HashMap<>();

    /**
     * Constructs a new GUI
     *
     * @param plugin the main plugin
     * @param rows the amount of rows this gui should contain
     * @param title the title/name of this gui
     */
    public Gui(Plugin plugin, int rows, String title) {
        assert rows >= 1 && rows <= 6 : "amount of rows outside range";

        this.panes = new ArrayList<>();
        this.inventory = Bukkit.createInventory(this, rows * 9, title);

        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Adds a pane to this gui
     *
     * @param pane the pane to add
     */
    public void addPane(Pane pane) {
        this.panes.add(pane);

        this.panes.sort(Comparator.comparing(Pane::getPriority));
    }

    /**
     * Shows a gui to a player
     *
     * @param humanEntity the human entity to show the gui to
     */
    public void show(HumanEntity humanEntity) {
        inventory.clear();

        //initialize the inventory first
        panes.stream().filter(Pane::isVisible).forEach(pane -> pane.display(inventory, 0, 0,
                9, getRows()));

        humanEntity.openInventory(inventory);
    }

    /**
     * Sets the amount of rows for this inventory. This will (unlike most other methods) directly update itself in order
     * to ensure all viewers will still be viewing the new inventory as well.
     *
     * @param rows the amount of rows
     */
    public void setRows(int rows) {
        assert rows >= 1 && rows <= 6 : "rows should be between 1 and 6";

        //copy the viewers
        List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());

        this.inventory = Bukkit.createInventory(this, rows * 9, this.inventory.getTitle());

        viewers.forEach(humanEntity -> humanEntity.openInventory(inventory));
    }

    /**
     * Gets all the panes in this gui, this includes child panes from other panes
     *
     * @return all panes
     */
    @NotNull
    @Contract(pure = true)
    public Collection<Pane> getPanes() {
        Collection<Pane> panes = new HashSet<>();

        this.panes.forEach(pane -> panes.addAll(pane.getPanes()));
        panes.addAll(this.panes);

        return panes;
    }

    /**
     * Sets the title for this inventory. This will (unlike most other methods) directly update itself in order
     * to ensure all viewers will still be viewing the new inventory as well.
     *
     * @param title the title
     */
    public void setTitle(String title) {
        //copy the viewers
        List<HumanEntity> viewers = new ArrayList<>(inventory.getViewers());

        this.inventory = Bukkit.createInventory(this, this.inventory.getSize(), title);

        viewers.forEach(humanEntity -> humanEntity.openInventory(inventory));
    }

    /**
     * Gets all the items in all underlying panes
     *
     * @return all items
     */
    @NotNull
    @Contract(pure = true)
    public Collection<GuiItem> getItems() {
        return getPanes().stream().flatMap(pane -> pane.getItems().stream()).collect(Collectors.toSet());
    }

    /**
     * Update the gui for everyone
     *
     * @since 5.6.0
     */
    public void update() {
        new HashSet<>(inventory.getViewers()).forEach(this::show);
    }

    /**
     * Loads a Gui from a given input stream
     *
     * @param plugin the main plugin
     * @param instance the class instance for all reflection lookups
     * @param inputStream the file
     * @return the gui
     * @since 5.6.0
     */
    @Nullable
    @Contract("_, _, null -> fail")
    public static Gui load(Plugin plugin, Object instance, InputStream inputStream) {
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(inputStream);
            Element documentElement = document.getDocumentElement();

            documentElement.normalize();

            Gui gui = new Gui(plugin, Integer.parseInt(documentElement.getAttribute("rows")), ChatColor
                    .translateAlternateColorCodes('&', documentElement.getAttribute("title")));

            if (documentElement.hasAttribute("field")) {
                try {
                    Field field = instance.getClass().getField(documentElement.getAttribute("field"));

                    field.setAccessible(true);
                    field.set(instance, gui);
                } catch (NoSuchFieldException | IllegalAccessException e) {
                    e.printStackTrace();
                }
            }

            if (documentElement.hasAttribute("onLocalClick"))
                gui.setOnLocalClick(XMLUtil.loadOnClickAttribute(instance, documentElement));
            
            if (documentElement.hasAttribute("onGlobalClick")) {
                for (Method method : instance.getClass().getMethods()) {
                    if (!method.getName().equals(documentElement.getAttribute("onGlobalClick")))
                        continue;

                    int parameterCount = method.getParameterCount();

                    if (parameterCount == 0) {
                        gui.setOnGlobalClick(event -> {
                            try {
                                method.setAccessible(true);
                                method.invoke(instance);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        });
                    } else if (parameterCount == 1 &&
                            InventoryClickEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        gui.setOnGlobalClick(event -> {
                            try {
                                method.setAccessible(true);
                                method.invoke(instance, event);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }

            if (documentElement.hasAttribute("onClose")) {
                for (Method method : instance.getClass().getMethods()) {
                    if (!method.getName().equals(documentElement.getAttribute("onClose")))
                        continue;

                    int parameterCount = method.getParameterCount();

                    if (parameterCount == 0) {
                        gui.setOnClose(event -> {
                            try {
                                method.setAccessible(true);
                                method.invoke(instance);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        });
                    } else if (parameterCount == 1 &&
                            InventoryCloseEvent.class.isAssignableFrom(method.getParameterTypes()[0])) {
                        gui.setOnClose(event -> {
                            try {
                                method.setAccessible(true);
                                method.invoke(instance, event);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                e.printStackTrace();
                            }
                        });
                    }
                }
            }

            if (documentElement.hasAttribute("populate")) {
                try {
                    Method method = instance.getClass().getMethod("populate", Gui.class);

                    method.setAccessible(true);
                    method.invoke(instance, gui);
                } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
                    e.printStackTrace();
                }

                return gui;
            }

            NodeList childNodes = documentElement.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);

                if (item.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                gui.addPane(loadPane(instance, item));
            }

            return gui;
        } catch (ParserConfigurationException | SAXException | IOException | NumberFormatException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Set the consumer that should be called whenever this gui is clicked in.
     *
     * @param onLocalClick the consumer that gets called
     */
    public void setOnLocalClick(Consumer<InventoryClickEvent> onLocalClick) {
        this.onLocalClick = onLocalClick;
    }

    /**
     * Set the consumer that should be called whenever this gui is closed.
     *
     * @param onClose the consumer that gets called
     */
    public void setOnClose(Consumer<InventoryCloseEvent> onClose) {
        this.onClose = onClose;
    }

    /**
     * Returns the amount of rows this gui currently has
     *
     * @return the amount of rows
     */
    public int getRows() {
        return inventory.getSize() / 9;
    }

    /**
     * Returns the title of this gui
     *
     * @return the title
     */
    public String getTitle() {
        return inventory.getTitle();
    }

    /**
     * {@inheritDoc}
     *
     * @since 5.6.0
     */
    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    /**
     * Set the consumer that should be called whenever this gui or inventory is clicked in.
     *
     * @param onGlobalClick the consumer that gets called
     */
    public void setOnGlobalClick(Consumer<InventoryClickEvent> onGlobalClick) {
        this.onGlobalClick = onGlobalClick;
    }

    /**
     * Registers a property that can be used inside an XML file to add additional new properties.
     *
     * @param attributeName the name of the property. This is the same name you'll be using to specify the property
     *                      type in the XML file.
     * @param function how the property should be processed. This converts the raw text input from the XML node value
     *                 into the correct object type.
     * @throws AssertionError when a property with this name is already registered.
     */
    public static void registerProperty(String attributeName, Function<String, Object> function) {
        assert !Pane.getPropertyMappings().containsKey(attributeName) : "property is already registered";

        Pane.getPropertyMappings().put(attributeName, function);
    }

    /**
     * Registers a name that can be used inside an XML file to add custom panes
     *
     * @param name the name of the pane to be used in the XML file
     * @param biFunction how the pane loading should be processed
     * @throws AssertionError when a pane with this name is already registered
     */
    public static void registerPane(String name, BiFunction<Object, Element, Pane> biFunction) {
        assert !PANE_MAPPINGS.containsKey(name) : "pane name already registered";

        PANE_MAPPINGS.put(name, biFunction);
    }

    /**
     * Loads a pane by the given instance and node
     *
     * @param instance the instance
     * @param node the node
     * @return the pane
     */
    @Nullable
    @Contract("_, null -> fail")
    public static Pane loadPane(Object instance, @NotNull Node node) {
        return PANE_MAPPINGS.get(node.getNodeName()).apply(instance, (Element) node);
    }

    /**
     * Handles clicks in inventories
     * 
     * @param event the event fired
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null || !this.equals(event.getClickedInventory().getHolder())) {
            if (this.equals(event.getInventory().getHolder()))
                if (onGlobalClick != null)
                    onGlobalClick.accept(event);
            return;
        }

        if (onLocalClick != null)
            onLocalClick.accept(event);

        //loop through the panes reverse, because the pane with the highest priority (last in list) is most likely to have the correct item
        for (int i = panes.size() - 1; i >= 0; i--) {
            if (panes.get(i).click(event, 0, 0, 9, getRows()))
                break;
        }
    }

    /**
     * Handles closing in inventories
     *
     * @param event the event fired
     */
    @EventHandler(ignoreCancelled = true)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (onClose == null)
            return;

        onClose.accept(event);
    }

    static {
        registerPane("outlinepane", OutlinePane::load);
        registerPane("paginatedpane", PaginatedPane::load);
        registerPane("staticpane", StaticPane::load);
    }
}
