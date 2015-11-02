package io.scalechain.blockchain

import java.io.DataInputStream

/**
 * Parse a block from a byte array stream.
 */
class BlockParser {
  /** Parse the byte array stream to get a block.
    *
    * This function reads the following fields.
    * Magic no : value always 0xD9B4BEF9, 4 bytes
    * Blocksize : number of bytes following up to end of block, 4 bytes
    * Blockheader : consists of 6 items, 80 bytes
    * Transaction counter : positive integer VI = VarInt, 1 - 9 bytes
    * transactions : the (non empty) list of transactions, <Transaction counter>-many transactions
    *
    * Source : https://en.bitcoin.it/wiki/Block
    * @param stream The stream where we read block data.
    *
    * @return Some(Block) if we successfully read a block. None otherwise.
    */
  def parse(stream : BlockDataInputStream) : Option[Block] = {
    val magic            = stream.readLittleEndianInt()

    // BUGBUG : Does this work even though the integer we read is a signed int?
    if (magic != 0xD9B4BEF9)
      throw new FatalException(ErrorCode.InvalidBlockMagic)

    val blockSize        = stream.readLittleEndianInt()
    val blockHeader      = readBlockHeader(stream);
    val transactionCount = stream.readVarInt()
    val transactions     = readTransactions(stream, transactionCount);

    val block = Block(blockSize, blockHeader, transactionCount, transactions)
    Some(block)
  }


  /** Read a block header
   * List of items to read for a block header.
   *
   * Version : Block version number	You upgrade the software and it specifies a new version, 4 bytes
   * hashPrevBlock : 256-bit hash of the previous block header	A new block comes in, 32 bytes
   * hashMerkleRoot : 256-bit hash based on all of the transactions in the block	A transaction is accepted, 32 bytes
   * Time : Current timestamp as seconds since 1970-01-01T00:00 UTC	Every few seconds, 4 bytes
   * Bits : Current target in compact format	The difficulty is adjusted, 4 bytes
   * Nonce : 32-bit number (starts at 0)	A hash is tried (increments), 4 bytes
   *
   * Source : https://en.bitcoin.it/wiki/Block_hashing_algorithm
   *
   * @param stream The stream where we read data.
   *
   * @return Return the block header we read.
   */
  def readBlockHeader(stream : BlockDataInputStream) : BlockHeader = {
    assert(false);
    val version         = stream.readLittleEndianInt()
    val hashPrevBlock   = Hash( stream.readBytes(32) )
    val hashMerkleRoot  = Hash( stream.readBytes(32) )
    val timestamp       = Timestamp( stream.readLittleEndianInt() )
    val target          = stream.readLittleEndianInt()
    val nonce           = stream.readLittleEndianInt()
    BlockHeader(version, hashPrevBlock, hashMerkleRoot, timestamp, target, nonce)
  }

  /** Read N transactions in a block
   *
   * @param stream The stream where we read data.
   * @param count The number of transactions we need to read.
   * @return Return an array of transactions we read.
   */
  def readTransactions(stream : BlockDataInputStream, count : Int) : Array[Transaction] = {
    val transactions = new Array[Transaction](count);
    for ( i <- 0 until count ) {
      transactions(i) = readTransaction(stream)
    }
    transactions
  }


  /** Read a transaction in a block
   *
   * List of items to read for a transaction.
   *
   * Version no : currently 1, 4 bytes
   * In-counter : positive integer VI = VarInt, 1 - 9 bytes
   * list of inputs : the first input of the first transaction is also called "coinbase" (its content was ignored in earlier versions), <in-counter>-many inputs
   * Out-counter : positive integer VI = VarInt, 1 - 9 bytes
   * list of outputs : the outputs of the first transaction spend the mined bitcoins for the block, <out-counter>-many outputs
   * lock_time : if non-zero and sequence numbers are < 0xFFFFFFFF: block height or timestamp when transaction is final, 4 bytes
   *
   * Source : https://en.bitcoin.it/wiki/Transaction
   *
   * @param stream The stream where we read data.
   * @return The transaction we read.
   */
  def readTransaction(stream : BlockDataInputStream) : Transaction = {
    val version = stream.readLittleEndianInt()
    val inputCount = stream.readVarInt()
    val inputs = readTransactionInputs(stream, inputCount)
    val outputCount = stream.readVarInt()
    val outputs = readTransactionOutputs(stream, outputCount)
    val lockTime = stream.readLittleEndianInt()
    Transaction(version, inputCount, inputs, outputCount, outputs, lockTime)
  }

  /** Read N transaction inputs.
   *
   * @param stream The stream where we read data.
   * @param inputCount the number of transaction inputs to read.
   * @return the transaction inputs we read.
   */
  def readTransactionInputs(stream : BlockDataInputStream, inputCount : Int) : Array[TransactionInput] = {
    val transactionInputs = new Array[TransactionInput](inputCount)

    for( i <- 0 until inputCount) {
      transactionInputs(i) = readTransactionInput(stream)
    }

    transactionInputs
  }

  /** Read N transaction outputs.
   *
   * @param stream The stream where we read data.
   * @param outputCount the number of transaction outputs to read.
   * @return the transaction outputs we read.
   */
  def readTransactionOutputs(stream : BlockDataInputStream, outputCount : Int) : Array[TransactionOutput] = {
    val transactionOutputs = new Array[TransactionOutput](outputCount)

    for( i <- 0 until outputCount) {
      transactionOutputs(i) = readTransactionOutput(stream)
    }

    transactionOutputs
  }


  /** Read either a generation transaction input or a normal transaction input.
   *
   * @param stream The stream where we read data.
   * @return the transaction input we read.
   */
  def readTransactionInput(stream : BlockDataInputStream) : TransactionInput = {

    val transactionHash = Hash( stream.readBytes(32) );

    if (transactionHash.isAllZero()) {
      readGenerationTranasctionInput(stream, transactionHash)
    } else {
      readNormalTransactionInput(stream, transactionHash)
    }
  }

  /**
   * List of items to read for a generation transaction input.
   *
   * Transaction Hash : All bits are zero: Not a transaction hash reference, 32 bytes
   * Output Index : All bits are ones: 0xFFFFFFFF, 4 bytes
   * Coinbase Data Size : Length of the coinbase data, from 2 to 100 bytes, 1-9 bytes (VarInt)
   * Coinbase Data : Arbitrary data used for extra nonce and mining tags in v2 blocks, must begin with block height, Variable
   * Sequence Number : Set to 0xFFFFFFFF, 4 bytes
   *
   * Source : https://github.com/aantonop/bitcoinbook/blob/develop/ch08.asciidoc
   *
   * @param stream The stream where we read data.
   * @param transactionHash The transaction hash, which is already read.
   * @return the generation transaction input we read.
   */
  def readGenerationTranasctionInput(stream : BlockDataInputStream, transactionHash : Hash) : TransactionInput = {

    // Note : The transaction hash is already read to branch our code to read which kind of transaction input.
    val outputIndex = stream.readLittleEndianInt()
    val coinbaseDataSize = stream.readVarInt()
    val coinbaseData = CoinbaseData(stream.readBytes(coinbaseDataSize))
    val sequenceNumber = stream.readLittleEndianInt()
    GenerationTransactionInput(transactionHash, outputIndex, coinbaseData, sequenceNumber)
  }

  /**
   * List of items to read for a normal transaction input.
   *
   * Transaction Hash : Pointer to the transaction containing the UTXO to be spent, 32 bytes
   * Output Index : The index number of the UTXO to be spent, first one is 0, 4 bytes
   * Unlocking-Script Size : Unlocking-Script length in bytes, to follow, 1-9 bytes (VarInt)
   * Unlocking-Script : A script that fulfills the conditions of the UTXO locking script, Variable
   * Sequence Number : Currently disabled Tx-replacement feature, set to 0xFFFFFFFF, 4 bytes
   *
   * Source : https://github.com/aantonop/bitcoinbook/blob/develop/ch08.asciidoc
   *
   * @param stream The stream where we read data.
   * @param transactionHash The transaction hash, which is already read.
   * @return the normal transaction input we read.
   */
  def readNormalTransactionInput(stream : BlockDataInputStream, transactionHash : Hash) : TransactionInput = {
    // Note : The transaction hash is already read to branch our code to read which kind of transaction input.
    val outputIndex = stream.readLittleEndianInt()
    val unlockingScriptSize = stream.readVarInt()
    val unlockingScript = readScript(stream, unlockingScriptSize)
    val sequenceNumber = stream.readLittleEndianInt()

    NormalTransactionInput(transactionHash, outputIndex, unlockingScript, sequenceNumber)
  }


  /** Read a transaction output.
   *
   * List of items to read for a normal transaction output.
   *
   * Value : non negative integer giving the number of Satoshis(BTC/10^8) to be transfered, 8 bytes
   * Txout-script length : non negative integer, 1 - 9 bytes VI = VarInt
   * Txout-script : <out-script length>-many bytes
   *
   * Source : https://en.bitcoin.it/wiki/Transaction#general_format_.28inside_a_block.29_of_each_output_of_a_transaction_-_Txout
   * @param stream The stream where we read data.
   * @return the transction output we read.
   */
  def readTransactionOutput(stream : BlockDataInputStream) : TransactionOutput = {
    val value = stream.readLittleEndianLong()
    val txOutScriptLength = stream.readVarInt()
    val txOutScript = readScript(stream, txOutScriptLength)
    TransactionOutput(value, txOutScript)
  }


  /** Read script from a byte stream.
   *
   * Source : https://en.bitcoin.it/wiki/Script
   *
   * @param stream The stream where we read data.
   * @return the script we read.
   */
  def readScript(stream : BlockDataInputStream, scriptLength : Int ) : Script = {
    Script( stream.readBytes(scriptLength) )
  }
}

