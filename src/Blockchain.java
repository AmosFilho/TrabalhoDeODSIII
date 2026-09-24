import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Blockchain {

  private int difficulty;
  private List<Block> blocks;

  public Blockchain(int difficulty) {
	  
    this.difficulty = difficulty;
    blocks = new ArrayList<>();
    
    // cria o primeiro block — previousHash = "0" (convenção: sem bloco anterior)
    Block b = new Block(0, System.currentTimeMillis(), "0", "Block gênesis");
    b.proofOfWork(difficulty);
    blocks.add(b);
  }

  public int getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(int difficulty) {
    this.difficulty = difficulty;
  }

  public List<Block> getBlocks() {
    return Collections.unmodifiableList(blocks);
  }

  public Block latestBlock() {
    return blocks.get(blocks.size() - 1);
  }

  public Block newBlock(String data) {
    Block latestBlock = latestBlock();
    
    return new Block(latestBlock.getIndex() + 1, System.currentTimeMillis(),
        latestBlock.getHash(), data);
  }

  public void addBlock(Block b) {
    if (b != null) {
      b.proofOfWork(difficulty);
      blocks.add(b);
    }
  }

  /**
   * Adiciona um bloco já reconstruído (lido do ledger) sem refazer proof-of-work.
   * O bloco deve ser criado com o construtor de reconstrução de Block.
   */
  public void addReconstructedBlock(Block b) {
    if (b != null) {
      blocks.add(b);
    }
  }

  public boolean isFirstBlockValid() {
    Block firstBlock = blocks.get(0);

    if (firstBlock.getIndex() != 0) {
      return false;
    }

    // O genesis usa previousHash = "0" (convenção)
    if (firstBlock.getPreviousHash() == null || !firstBlock.getPreviousHash().equals("0")) {
      return false;
    }

    if (firstBlock.getHash() == null || 
          !Block.calculateHash(firstBlock).equals(firstBlock.getHash())) {
      return false;
    }

    return true;
  }

  public boolean isValidNewBlock(Block newBlock, Block previousBlock) {
    if (newBlock != null  &&  previousBlock != null) {
      if (previousBlock.getIndex() + 1 != newBlock.getIndex()) {
        return false;
      }

      if (newBlock.getPreviousHash() == null  ||  
	    !newBlock.getPreviousHash().equals(previousBlock.getHash())) {
        return false;
      }

      if (newBlock.getHash() == null  ||  
	    !Block.calculateHash(newBlock).equals(newBlock.getHash())) {
        return false;
      }

      return true;
    }

    return false;
  }

  public boolean isBlockChainValid() {
    if (!isFirstBlockValid()) {
      return false;
    }

    for (int i = 1; i < blocks.size(); i++) {
      Block currentBlock = blocks.get(i);
      Block previousBlock = blocks.get(i - 1);

      if (!isValidNewBlock(currentBlock, previousBlock)) {
        return false;
      }
    }

    return true;
  }

  public String toString() {
    StringBuilder builder = new StringBuilder();

    for (Block block : blocks) {
      builder.append(block).append("\n");
    }

    return builder.toString();
  }

}
