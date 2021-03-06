package com.github.stefvanschie.inventoryframework.pane;

import com.github.stefvanschie.inventoryframework.GuiLocation;
import com.github.stefvanschie.inventoryframework.GuiItem;
import com.github.stefvanschie.inventoryframework.pane.util.Pane;
import com.github.stefvanschie.inventoryframework.util.GeometryUtil;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.*;
import java.util.function.Consumer;

/**
 * A pane for items that should be outlined
 */
public class OutlinePane extends Pane {

    /**
     * A set of items inside this pane
     */
    @NotNull
    private final List<GuiItem> items;

    /**
     * The orientation of the items in this pane
     */
    @NotNull
    private Orientation orientation;

    /**
     * The clockwise rotation of this pane in degrees
     */
    private int rotation;

    /**
     * The amount of empty spots in between each item
     */
    private int gap;

    /**
     * Whether the items should be repeated to fill the entire pane
     */
    private boolean repeat;

    /**
     * Whether the items should be flipped horizontally and/or vertically
     */
    private boolean flipHorizontally, flipVertically;

    /**
     * Constructs a new default pane
     *
     * @param start  the upper left corner of the pane
     * @param length the length of the pane
     * @param height the height of the pane
     */
    public OutlinePane(@NotNull GuiLocation start, int length, int height) {
        super(start, length, height);

        this.items = new ArrayList<>(length * height);
        this.orientation = Orientation.HORIZONTAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void display(@NotNull Inventory inventory, int paneOffsetX, int paneOffsetY, int maxLength, int maxHeight) {
        int length = Math.min(this.length, maxLength);
        int height = Math.min(this.height, maxHeight);

        int x = 0;
        int y = 0;

        for (int i = 0; i < (repeat ? length * height : items.size()); i++) {
            GuiItem item = items.get(i % items.size());

            if (!item.isVisible())
                continue;

            int newX = x, newY = y;

            if (flipHorizontally)
                newX = length - x - 1;

            if (flipVertically)
                newY = height - y - 1;

            Map.Entry<Integer, Integer> coordinates = GeometryUtil.processClockwiseRotation(newX, newY, length, height,
                    rotation);

            inventory.setItem((start.getY() + coordinates.getValue() + paneOffsetY) * 9 + (start.getX() + coordinates
                    .getKey() + paneOffsetX), item.getItem());

            //increment positions
            if (orientation == Orientation.HORIZONTAL) {
                x += gap + 1;

                if (x >= length) {
                    y += x / length;
                    x %= length;
                }
            } else if (orientation == Orientation.VERTICAL) {
                y += gap + 1;

                if (y >= height) {
                    x += y / height;
                    y %= height;
                }
            }

            //stop the loop when there is no more space in the pane
            if (x >= length || y >= height)
                break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean click(@NotNull InventoryClickEvent event, int paneOffsetX, int paneOffsetY, int maxLength,
                         int maxHeight) {
        int length = Math.min(this.length, maxLength);
        int height = Math.min(this.height, maxHeight);

        int slot = event.getSlot();

        //correct coordinates
        int x = (slot % 9) - start.getX() - paneOffsetX;
        int y = (slot / 9) - start.getY() - paneOffsetY;

        //this isn't our item
        if (x < 0 || x > length || y < 0 || y > height)
            return false;

        Map.Entry<Integer, Integer> coordinates = GeometryUtil.processCounterClockwiseRotation(x, y, length, height, rotation);

        int newX = coordinates.getKey(), newY = coordinates.getValue();

        if (flipHorizontally)
            newX = length - newX - 1;

        if (flipVertically)
            newY = height - newY - 1;

        int index = 0;

        //adjust for gap
        if (orientation == Orientation.HORIZONTAL)
            index = newY * length + newX;
        else if (orientation == Orientation.VERTICAL)
            index = newX * height + newY;

        index /= gap + 1;

        if (items.size() <= index && repeat)
            index %= items.size();
        else if (items.size() <= index)
            return false;

        GuiItem item = items.get(index);

        if (!item.getItem().equals(event.getCurrentItem()) || !item.isVisible())
            return false;

        Consumer<InventoryClickEvent> action = item.getAction();

        if (action != null)
            action.accept(event);

        return true;
    }

    /**
     * Sets the rotation of this pane. The rotation is in degrees and can only be in increments of 90. Anything higher
     * than 360, will be lowered to a value in between [0, 360) while maintaining the same rotational value. E.g. 450
     * degrees becomes 90 degrees, 1080 degrees becomes 0, etc.
     *
     * This method fails for any pane that has a length and height which are unequal.
     *
     * @param rotation the rotation of this pane
     * @throws AssertionError when the length and height of the pane are not the same
     */
    public void setRotation(int rotation) {
        assert length == height : "length and height are different";
        assert rotation % 90 == 0 : "rotation isn't divisible by 90";

        this.rotation = rotation % 360;
    }

    /**
     * Adds a gui item in the specified index
     *
     * @param item the item to add
     * @param index the item's index
     */
    public void insertItem(@NotNull GuiItem item, int index) {
        items.add(index, item);
    }

    /**
     * Adds a gui item at the specific spot in the pane
     *
     * @param item the item to set
     */
    public void addItem(@NotNull GuiItem item) {
        items.add(item);
    }

    /**
     * Sets whether this pane should flip its items horizontally
     *
     * @param flipHorizontally whether the pane should flip items horizontally
     */
    public void flipHorizontally(boolean flipHorizontally) {
        this.flipHorizontally = flipHorizontally;
    }

    /**
     * Sets whether this pane should flip its items vertically
     *
     * @param flipVertically whether the pane should flip items vertically
     */
    public void flipVertically(boolean flipVertically) {
        this.flipVertically = flipVertically;
    }

    /**
     * Sets the gap of the pane
     *
     * @param gap the new gap
     */
    public void setGap(int gap) {
        this.gap = gap;
    }

    /**
     * Sets the orientation of this outline pane
     *
     * @param orientation the new orientation
     */
    public void setOrientation(@NotNull Orientation orientation) {
        this.orientation = orientation;
    }

    /**
     * Sets whether this pane should repeat itself
     *
     * @param repeat whether the pane should repeat
     */
    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    /**
     * {@inheritDoc}
     */
    @NotNull
    @Contract(pure = true)
    @Override
    public Collection<Pane> getPanes() {
        return new HashSet<>();
    }

    /**
     * Gets whether this outline pane repeats itself
     *
     * @return true if this pane repeats, false otherwise
     */
    @Contract(pure = true)
    public boolean doesRepeat() {
        return repeat;
    }

    /**
     * Gets the gap of the pane
     *
     * @return the gap
     */
    @Contract(pure = true)
    public int getGap() {
        return gap;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NotNull
    public List<GuiItem> getItems() {
        return items;
    }

    /**
     * Gets the orientation of this outline pane
     *
     * @return the orientation
     */
    @NotNull
    @Contract(pure = true)
    public Orientation getOrientation() {
        return orientation;
    }

    /**
     * Gets the rotation specified to this pane. If no rotation has been set, or if this pane is not capable of having a
     * rotation, 0 is returned.
     *
     * @return the rotation for this pane
     */
    @Contract(pure = true)
    public int getRotation() {
        return rotation;
    }

    /**
     * Gets whether this pane's items are flipped horizontally
     *
     * @return true if the items are flipped horizontally, false otherwise
     */
    @Contract(pure = true)
    public boolean isFlippedHorizontally() {
        return flipHorizontally;
    }

    /**
     * Gets whether this pane's items are flipped vertically
     *
     * @return true if the items are flipped vertically, false otherwise
     */
    @Contract(pure = true)
    public boolean isFlippedVertically() {
        return flipVertically;
    }

    /**
     * Loads an outline pane from a given element
     *
     * @param instance the instance class
     * @param element the element
     * @return the outline pane
     */
    @Nullable
    @Contract("_, null -> fail")
    public static OutlinePane load(Object instance, @NotNull Element element) {
        try {
            OutlinePane outlinePane = new OutlinePane(new GuiLocation(
                Integer.parseInt(element.getAttribute("x")),
                Integer.parseInt(element.getAttribute("y"))),
                Integer.parseInt(element.getAttribute("length")),
                Integer.parseInt(element.getAttribute("height"))
            );

            if (element.hasAttribute("rotation"))
                outlinePane.setRotation(Integer.parseInt(element.getAttribute("rotation")));

            if (element.hasAttribute("orientation"))
                outlinePane.setOrientation(Orientation.valueOf(element.getAttribute("orientation")
                    .toUpperCase(Locale.getDefault())));

            if (element.hasAttribute("gap"))
                outlinePane.setGap(Integer.parseInt(element.getAttribute("gap")));

            if (element.hasAttribute("repeat"))
                outlinePane.setRepeat(Boolean.parseBoolean(element.getAttribute("repeat")));

            if (element.hasAttribute("flipHorizontally"))
                outlinePane.flipHorizontally(Boolean.parseBoolean(element.getAttribute("flipHorizontally")));

            if (element.hasAttribute("flipVertically"))
                outlinePane.flipVertically(Boolean.parseBoolean(element.getAttribute("flipVertically")));

            Pane.load(outlinePane, instance, element);

            if (element.hasAttribute("populate"))
                return outlinePane;

            NodeList childNodes = element.getChildNodes();

            for (int i = 0; i < childNodes.getLength(); i++) {
                Node item = childNodes.item(i);

                if (item.getNodeType() != Node.ELEMENT_NODE)
                    continue;

                if (item.getNodeName().equals("empty"))
                    outlinePane.addItem(new GuiItem(new ItemStack(Material.AIR)));
                else
                    outlinePane.addItem(Pane.loadItem(instance, (Element) item));
            }

            return outlinePane;
        } catch (NumberFormatException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * An orientation for outline panes
     */
    public enum Orientation {

        /**
         * A horizontal orientation, will outline every item from the top-left corner going to the right and down
         */
        HORIZONTAL,

        /**
         * A vertical orientation, will outline every item from the top-left corner going down and to the right
         */
        VERTICAL
    }
}