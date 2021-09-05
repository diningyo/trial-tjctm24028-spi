package ili9341.sequencer

import chisel3._
import chisel3.util._

object Commands {
  //val ILI9341_TFTWIDTH 240  ///< ILI9341 max TFT width
  //val ILI9341_TFTHEIGHT 320 ///< ILI9341 max TFT height

  val ILI9341_NOP         = 0x00   ///< No-op register
  val ILI9341_SWRESET     = 0x01   ///< Software reset register
  val ILI9341_RDDID       = 0x04   ///< Read display identification information
  val ILI9341_RDDST       = 0x09   ///< Read Display Status

  val ILI9341_SLPIN       = 0x10   ///< Enter Sleep Mode
  val ILI9341_SLPOUT      = 0x11   ///< Sleep Out
  val ILI9341_PTLON       = 0x12   ///< Partial Mode ON
  val ILI9341_NORON       = 0x13   ///< Normal Display Mode ON

  val ILI9341_RDMODE      = 0x0A   ///< Read Display Power Mode
  val ILI9341_RDMADCTL    = 0x0B   ///< Read Display MADCTL
  val ILI9341_RDPIXFMT    = 0x0C   ///< Read Display Pixel Format
  val ILI9341_RDIMGFMT    = 0x0D   ///< Read Display Image Format
  val ILI9341_RDSELFDIAG  = 0x0F   ///< Read Display Self-Diagnostic Result

  val ILI9341_INVOFF      = 0x20   ///< Display Inversion OFF
  val ILI9341_INVON       = 0x21   ///< Display Inversion ON
  val ILI9341_GAMMASET    = 0x26   ///< Gamma Set
  val ILI9341_DISPOFF     = 0x28   ///< Display OFF
  val ILI9341_DISPON      = 0x29   ///< Display ON

  val ILI9341_CASET       = 0x2A   ///< Column Address Set
  val ILI9341_PASET       = 0x2B   ///< Page Address Set
  val ILI9341_RAMWR       = 0x2C   ///< Memory Write
  val ILI9341_RAMRD       = 0x2E   ///< Memory Read

  val ILI9341_PTLAR       = 0x30   ///< Partial Area
  val ILI9341_VSCRDEF     = 0x33   ///< Vertical Scrolling Definition
  val ILI9341_MADCTL      = 0x36   ///< Memory Access Control
  val ILI9341_VSCRSADD    = 0x37   ///< Vertical Scrolling Start Address
  val ILI9341_PIXFMT      = 0x3A   ///< COLMOD: Pixel Format Set

  val ILI9341_FRMCTR1     = 0xB1   ///< Frame Rate Control (In Normal Mode/Full Colors)
  val ILI9341_FRMCTR2     = 0xB2   ///< Frame Rate Control (In Idle Mode/8 colors)
  val ILI9341_FRMCTR3     = 0xB3   ///< Frame Rate control (In Partial Mode/Full Colors)
  val ILI9341_INVCTR      = 0xB4   ///< Display Inversion Control
  val ILI9341_DFUNCTR     = 0xB6   ///< Display Function Control

  val ILI9341_PWCTR1      = 0xC0   ///< Power Control 1
  val ILI9341_PWCTR2      = 0xC1   ///< Power Control 2
  val ILI9341_PWCTR3      = 0xC2   ///< Power Control 3
  val ILI9341_PWCTR4      = 0xC3   ///< Power Control 4
  val ILI9341_PWCTR5      = 0xC4   ///< Power Control 5
  val ILI9341_VMCTR1      = 0xC5   ///< VCOM Control 1
  val ILI9341_VMCTR2      = 0xC7   ///< VCOM Control 2

  val ILI9341_RDID1       = 0xDA   ///< Read ID 1
  val ILI9341_RDID2       = 0xDB   ///< Read ID 2
  val ILI9341_RDID3       = 0xDC   ///< Read ID 3
  val ILI9341_RDID4       = 0xDD   ///< Read ID 4

  val ILI9341_GMCTRP1     = 0xE0   ///< Positive Gamma Correction
  val ILI9341_GMCTRN1     = 0xE1   ///< Negative Gamma Correction
  //val ILI9341_PWCTR6    = 0xFC

                                   // Color definitions
  val ILI9341_BLACK       = 0x0000 ///<   0,   0,   0
  val ILI9341_NAVY        = 0x000F ///<   0,   0, 123
  val ILI9341_DARKGREEN   = 0x03E0 ///<   0, 125,   0
  val ILI9341_DARKCYAN    = 0x03EF ///<   0, 125, 123
  val ILI9341_MAROON      = 0x7800 ///< 123,   0,   0
  val ILI9341_PURPLE      = 0x780F ///< 123,   0, 123
  val ILI9341_OLIVE       = 0x7BE0 ///< 123, 125,   0
  val ILI9341_LIGHTGREY   = 0xC618 ///< 198, 195, 198
  val ILI9341_DARKGREY    = 0x7BEF ///< 123, 125, 123
  val ILI9341_BLUE        = 0x001F ///<   0,   0, 255
  val ILI9341_GREEN       = 0x07E0 ///<   0, 255,   0
  val ILI9341_CYAN        = 0x07FF ///<   0, 255, 255
  val ILI9341_RED         = 0xF800 ///< 255,   0,   0
  val ILI9341_MAGENTA     = 0xF81F ///< 255,   0, 255
  val ILI9341_YELLOW      = 0xFFE0 ///< 255, 255,   0
  val ILI9341_WHITE       = 0xFFFF ///< 255, 255, 255
  val ILI9341_ORANGE      = 0xFD20 ///< 255, 165,   0
  val ILI9341_GREENYELLOW = 0xAFE5 ///< 173, 255,  41
  val ILI9341_PINK        = 0xFC18 ///< 255, 130, 198

}
