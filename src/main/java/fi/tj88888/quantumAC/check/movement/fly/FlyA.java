package fi.tj88888.quantumAC.check.movement.fly;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketEvent;
import fi.tj88888.quantumAC.QuantumAC;
import fi.tj88888.quantumAC.check.movement.fly.components.GravityCheck;
import fi.tj88888.quantumAC.check.movement.fly.components.MotionInconsistencyCheck;
import fi.tj88888.quantumAC.check.movement.fly.components.TerminalVelocityCheck;
import fi.tj88888.quantumAC.check.movement.fly.components.VerticalAccelerationCheck;
import fi.tj88888.quantumAC.data.PlayerData;
import fi.tj88888.quantumAC.util.MovementData;
import org.bukkit.entity.Player;

/**
 * FlyA - Specialized in detecting gravity and vertical movement violations
 *
 * This check focuses on:
 * 1. Gravity violations (not falling when you should)
 * 2. Vertical acceleration analysis (going up faster than possible)
 * 3. Terminal velocity violations (falling too slowly)
 * 4. Vertical motion inconsistencies
 */
public class FlyA extends FlyCheck {

    // Component checks
    private final GravityCheck gravityCheck;
    private final VerticalAccelerationCheck accelerationCheck;
    private final TerminalVelocityCheck terminalVelocityCheck;
    private final MotionInconsistencyCheck motionInconsistencyCheck;

    // Additional tracking to reduce false positives
    private double lastHorizontalSpeed = 0.0;

    public FlyA(QuantumAC plugin, PlayerData playerData) {
        super(plugin, playerData, "Fly", "A");
        
        // Initialize components
        this.gravityCheck = new GravityCheck();
        this.accelerationCheck = new VerticalAccelerationCheck();
        this.terminalVelocityCheck = new TerminalVelocityCheck();
        this.motionInconsistencyCheck = new MotionInconsistencyCheck();
    }

    @Override
    public void processPacket(PacketEvent event) {
        if (!isMovementPacket(event.getPacketType())) {
            return;
        }

        // Skip if not a position packet
        if (event.getPacketType() != PacketType.Play.Client.POSITION && 
            event.getPacketType() != PacketType.Play.Client.POSITION_LOOK) {
            return;
        }

        Player player = playerData.getPlayer();
        if (player == null) return;

        // Get movement data
        MovementData movementData = playerData.getMovementData();
        MovementData previousMovementData = playerData.getPreviousMovementData();
        
        if (movementData == null || previousMovementData == null) {
            return;
        }

        // Calculate vertical movement
        double currentY = movementData.getY();
        double dy = currentY - lastY;
        boolean onGround = movementData.isOnGround();
        
        // Calculate horizontal movement for context
        double dx = movementData.getX() - previousMovementData.getX();
        double dz = movementData.getZ() - previousMovementData.getZ();
        lastHorizontalSpeed = Math.sqrt(dx * dx + dz * dz);

        // Update vertical movement history
        updateVerticalHistory(dy, currentY, onGround);

        // Check if player is exempt from checks
        boolean exempt = isExempt(player);
        
        // Get player effects
        boolean hasSlowFalling = hasSlowFalling(player);
        int jumpBoostLevel = getJumpBoostLevel(player);
        
        // Calculate tolerance based on conditions
        double tolerance = calculateTolerance(player);

        // Run component checks
        String gravityViolation = gravityCheck.checkGravityViolation(player, dy, onGround, exempt, tolerance);
        if (gravityViolation != null) {
            flag(1.0, gravityViolation);
            return;
        }

        String accelerationViolation = accelerationCheck.checkVerticalAcceleration(
            player, dy, onGround, exempt, jumpBoostLevel, tolerance);
        if (accelerationViolation != null) {
            flag(1.0, accelerationViolation);
            return;
        }

        String terminalVelocityViolation = terminalVelocityCheck.checkTerminalVelocity(
            player, dy, onGround, exempt, hasSlowFalling, tolerance);
        if (terminalVelocityViolation != null) {
            flag(1.0, terminalVelocityViolation);
            return;
        }

        String motionInconsistencyViolation = motionInconsistencyCheck.checkMotionInconsistency(
            player, dy, onGround, exempt, tolerance);
        if (motionInconsistencyViolation != null) {
            flag(1.0, motionInconsistencyViolation);
            return;
        }
    }
} 