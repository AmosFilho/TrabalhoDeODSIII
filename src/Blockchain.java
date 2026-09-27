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

  public void replaceGenesis(Block genesis) {
    if (genesis != null && genesis.getIndex() == 0) {
      blocks.set(0, genesis);
    }
  }

  public String validationError(int i) {
    Block b = blocks.get(i);

    if (b.getHash() == null || !b.getHash().equals(Block.calculateHash(b))) {
      return "Hash não confere com o conteúdo do bloco (dados adulterados?)";
    }

    if (i == 0) {
      // O genesis usa previousHash = "0" (convenção)
      if (b.getIndex() != 0 || !"0".equals(b.getPreviousHash())) {
        return "Gênesis deve ter índice 0 e hash anterior \"0\"";
      }
      return null;
    }

    Block previous = blocks.get(i - 1);

    if (previous.getIndex() + 1 != b.getIndex()) {
      return "Índice fora de sequência";
    }

    if (b.getPreviousHash() == null || !b.getPreviousHash().equals(previous.getHash())) {
      return "Hash anterior não aponta para o bloco #" + previous.getIndex();
    }

    return null;
  }

  public int repair() {
    int repaired = 0;

    for (int i = 0; i < blocks.size(); i++) {
      if (validationError(i) == null) {
        continue;
      }

      Block old = blocks.get(i);
      String previousHash = i == 0 ? "0" : blocks.get(i - 1).getHash();
      Block fixed = new Block(i, old.getTimestamp(), previousHash, old.getData());
      fixed.proofOfWork(difficulty);
      blocks.set(i, fixed);
      repaired++;
    }

    return repaired;
  }

  public boolean isFirstBlockValid() {
    Block firstBlock = blocks.get(0);

    if (firstBlock.getIndex() != 0) {
      return false;
    }

    return validationError(0) == null;
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
